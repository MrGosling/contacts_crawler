package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.config.ExecutorConfig;
import com.example.contactscrawler.config.CrawlerThreadFactory;
import com.example.contactscrawler.config.LoggingCallerRunsPolicy;
import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.logging.AsyncLogger;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.InMemoryContactRepository;
import com.example.contactscrawler.support.SandboxDispatcher;
import com.example.contactscrawler.support.TestSupport;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Обход sandbox (585 страниц) всеми движками на MockWebServer; дедупликация; остановка. */
class EnginesTest {

    @TempDir
    Path tmp;

    private MockWebServer server;
    private SandboxDispatcher sandbox;
    private CrawlerProperties props;
    private InMemoryContactRepository repo;
    private AsyncLogger asyncLogger;
    private PagePipeline pipeline;
    private ThreadPoolExecutor executor;
    private ForkJoinPool forkJoinPool;

    @BeforeEach
    void setUp() throws IOException {
        sandbox = new SandboxDispatcher(8, 3);
        server = new MockWebServer();
        server.setDispatcher(sandbox);
        server.start();
        props = TestSupport.props();
        props.setThreads(8);
        props.setPerHostConcurrency(8);
        repo = new InMemoryContactRepository();
        asyncLogger = TestSupport.asyncLogger(tmp);
        pipeline = TestSupport.pipeline(props, repo, asyncLogger);
        executor = new ExecutorConfig.GracefulThreadPoolExecutor(8, new ArrayBlockingQueue<>(100),
                new CrawlerThreadFactory("crawler-worker", false), new LoggingCallerRunsPolicy(), Duration.ofSeconds(5));
        forkJoinPool = ExecutorConfig.newForkJoinPool(4);
    }

    @AfterEach
    void tearDown() throws IOException {
        sandbox.release();
        executor.shutdownNow();
        forkJoinPool.shutdownNow();
        asyncLogger.close();
        server.shutdown();
    }

    private CrawlJob job() {
        CrawlJob job = new CrawlJob(new CrawlParams(List.of(server.url("/").uri()), 3, 10_000, true, 0));
        job.markRunning();
        return job;
    }

    private void assertFullCrawl(CrawlJob job) throws IOException {
        int total = sandbox.totalPages();
        assertThat(total).isEqualTo(585);
        assertThat(job.pagesFetched.sum()).isEqualTo(total);
        assertThat(job.pagesFailed.sum()).isZero();
        // каждая страница запрошена ровно один раз, запрещённые/внешние — ни разу
        for (int i = 1; i < total; i++) {
            assertThat(sandbox.hits("/p" + i)).as("hits /p" + i).isEqualTo(1);
        }
        assertThat(sandbox.hits("/")).isEqualTo(1);
        assertThat(sandbox.hits("/robots.txt")).isEqualTo(1);
        assertThat(sandbox.hits("/private/secret")).isZero();
        assertThat(sandbox.hits("/file.pdf")).isZero();
        assertThat(repo.findByFilter(ContactType.EMAIL, null)).hasSize(total + 1);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                Files.exists(asyncLogger.path()) && Files.readAllLines(asyncLogger.path()).size() >= total);
    }

    @Test
    void threadEngineCrawlsSandbox() throws IOException {
        ThreadCrawlEngine engine = new ThreadCrawlEngine(pipeline, props);
        CrawlJob job = job();
        engine.run(job);
        assertFullCrawl(job);
        assertThat(engine.visitedUrls()).isGreaterThanOrEqualTo(585);
        assertThat(engine.queueSize()).isZero();
        assertThat(engine.activeThreads()).isZero();
        assertThat(engine.poolSize()).isEqualTo(8);
        assertThat(engine.name()).isEqualTo("threads");
    }

    @Test
    void executorEngineCrawlsSandbox() throws IOException {
        ExecutorCrawlEngine engine = new ExecutorCrawlEngine(pipeline, executor, props);
        CrawlJob job = job();
        engine.run(job);
        assertFullCrawl(job);
        assertThat(engine.queueSize()).isZero();
        assertThat(engine.droppedTasks()).isZero();
        assertThat(engine.poolSize()).isEqualTo(8);
        assertThat(engine.name()).isEqualTo("executor");
    }

    @Test
    void forkJoinEngineCrawlsSandbox() throws IOException {
        ForkJoinCrawlEngine engine = new ForkJoinCrawlEngine(pipeline,
                new ParallelExtractionService(TestSupport.extraction(props), forkJoinPool), forkJoinPool);
        CrawlJob job = job();
        engine.run(job);
        assertFullCrawl(job);
        assertThat(engine.queueSize()).isZero();
        assertThat(engine.poolSize()).isEqualTo(4);
        assertThat(engine.name()).isEqualTo("forkjoin");
    }

    @Test
    void executorEngineDropsLinksOnQueueOverflow() {
        props.setQueueCapacity(5);
        ExecutorCrawlEngine engine = new ExecutorCrawlEngine(pipeline, executor, props);
        CrawlJob job = job();
        engine.run(job);
        assertThat(engine.droppedTasks()).isPositive();
        assertThat(job.pagesFetched.sum()).isLessThan(585);
    }

    private void assertStopsQuickly(CrawlEngine engine) {
        sandbox.blocking();
        CrawlJob job = job();
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> engine.run(job));
        await().atMost(Duration.ofSeconds(5)).until(() -> sandbox.inFlight() > 0);
        long start = System.nanoTime();
        job.markCancelled();
        engine.stop();
        run.orTimeout(5, TimeUnit.SECONDS).join();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(5000);
    }

    @Test
    void threadEngineStopsWithinFiveSeconds() {
        ThreadCrawlEngine engine = new ThreadCrawlEngine(pipeline, props);
        assertStopsQuickly(engine);
        await().atMost(Duration.ofSeconds(5)).until(() -> engine.activeThreads() == 0);
    }

    @Test
    void executorEngineStopsWithinFiveSeconds() {
        assertStopsQuickly(new ExecutorCrawlEngine(pipeline, executor, props));
    }

    @Test
    void noSeedsMeansImmediateReturn() {
        CrawlJob job = new CrawlJob(new CrawlParams(List.of(URI.create("https://a.ru/file.pdf")), 1, 10, true, 0));
        new ThreadCrawlEngine(pipeline, props).run(job);
        new ExecutorCrawlEngine(pipeline, executor, props).run(job);
        assertThat(job.pagesFetched.sum()).isZero();
    }
}
