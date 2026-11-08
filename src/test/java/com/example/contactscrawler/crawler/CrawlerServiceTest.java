package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.engine.CrawlEngine;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.crawler.model.JobStatus;
import com.example.contactscrawler.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class CrawlerServiceTest {

    /** Движок-заглушка: выполняет behavior и ждёт release()/stop(). */
    static class FakeEngine implements CrawlEngine {
        volatile CountDownLatch gate = new CountDownLatch(1);
        volatile Consumer<CrawlJob> behavior = j -> j.pagesFetched.increment();
        final AtomicInteger runs = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        @Override
        public void run(CrawlJob job) {
            runs.incrementAndGet();
            behavior.accept(job);
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void release() {
            gate.countDown();
        }

        @Override
        public void stop() {
            stops.incrementAndGet();
            gate.countDown();
        }

        @Override public int activeThreads() { return 1; }
        @Override public int poolSize() { return 2; }
        @Override public int queueSize() { return 3; }
        @Override public int visitedUrls() { return 4; }
        @Override public String name() { return "fake"; }
    }

    private FakeEngine engine;
    private CrawlerService service;

    private static CrawlParams params() {
        return new CrawlParams(List.of(URI.create("https://a.ru/")), 1, 10, true, 0);
    }

    @BeforeEach
    void setUp() {
        engine = new FakeEngine();
        service = new CrawlerService(new CrawlJobRegistry(), engine,
                new RobotsTxtChecker(mock(PageFetcher.class), TestSupport.props()));
        service.start();
    }

    @AfterEach
    void tearDown() {
        engine.release();
        service.shutdown();
    }

    @Test
    void oneActiveJobOthersQueued() {
        CrawlJob first = service.submit(params());
        CrawlJob second = service.submit(params());
        await().atMost(Duration.ofSeconds(5)).until(() -> first.status() == JobStatus.RUNNING);
        assertThat(second.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(service.status().currentJobId()).isEqualTo(first.id());
        assertThat(service.status().queuedJobs()).isEqualTo(1);
        assertThat(service.status().engine()).isEqualTo("fake");
        assertThat(service.jobResponse(first.id()).orElseThrow().queueSize()).isEqualTo(3);
        assertThat(service.jobResponse(second.id()).orElseThrow().queueSize()).isZero();

        assertThat(service.trySubmitScheduled(params())).isFalse(); // занят → плановый пропуск

        engine.release();
        await().atMost(Duration.ofSeconds(5)).until(() -> first.status() == JobStatus.DONE);
        engine.gate = new CountDownLatch(0);
        await().atMost(Duration.ofSeconds(5)).until(() -> second.status() == JobStatus.DONE);
        await().atMost(Duration.ofSeconds(5)).until(service::isIdle);
        assertThat(service.trySubmitScheduled(params())).isTrue();
        assertThat(first.finishedAt()).isNotNull();
    }

    @Test
    void cancelQueuedAndRunning() {
        CrawlJob first = service.submit(params());
        CrawlJob second = service.submit(params());
        await().atMost(Duration.ofSeconds(5)).until(() -> first.status() == JobStatus.RUNNING);
        assertThat(service.cancel(second.id())).isTrue();
        assertThat(second.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(service.cancel(first.id())).isTrue();
        assertThat(engine.stops.get()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(service::isIdle);
        assertThat(first.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(service.cancel(first.id())).isFalse();
        assertThat(service.cancel("missing")).isFalse();
        assertThat(engine.runs.get()).isEqualTo(1);
    }

    @Test
    void failedWhenNoSeedLoaded() {
        engine.gate = new CountDownLatch(0);
        engine.behavior = j -> j.pagesFailed.increment();
        CrawlJob job = service.submit(params());
        await().atMost(Duration.ofSeconds(5)).until(() -> job.status() == JobStatus.FAILED);
        assertThat(job.error()).contains("No seed");
    }

    @Test
    void failedOnEngineException() {
        engine.behavior = j -> {
            throw new IllegalStateException("boom");
        };
        CrawlJob job = service.submit(params());
        await().atMost(Duration.ofSeconds(5)).until(() -> job.status() == JobStatus.FAILED);
        assertThat(job.error()).contains("boom");
    }

    @Test
    void shutdownCancelsCurrentAndQueued() {
        CrawlJob first = service.submit(params());
        CrawlJob second = service.submit(params());
        await().atMost(Duration.ofSeconds(5)).until(() -> first.status() == JobStatus.RUNNING);
        service.shutdown();
        assertThat(first.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(second.status()).isEqualTo(JobStatus.CANCELLED);
        assertThatThrownBy(() -> service.submit(params())).isInstanceOf(IllegalStateException.class);
        assertThat(service.find(first.id())).isPresent();
    }
}
