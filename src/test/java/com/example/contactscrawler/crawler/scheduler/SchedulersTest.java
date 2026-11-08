package com.example.contactscrawler.crawler.scheduler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.CrawlerService;
import com.example.contactscrawler.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulersTest {

    @TempDir
    Path tmp;

    private CrawlerProperties props;
    private CrawlerService crawlerService;
    private SeedService seedService;
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        props = TestSupport.props();
        props.getScheduler().setInitialDelay(Duration.ofMillis(10));
        props.getScheduler().setFixedDelay(Duration.ofMillis(20));
        crawlerService = mock(CrawlerService.class);
        seedService = mock(SeedService.class);
        when(seedService.all()).thenReturn(List.of(URI.create("https://a.ru/")));
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void executorSchedulerRunsPeriodically() {
        when(crawlerService.trySubmitScheduled(any())).thenReturn(true);
        CrawlScheduler scheduler = new CrawlScheduler(crawlerService, seedService, executor, props);
        scheduler.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> scheduler.runs() >= 3);
        scheduler.stop();
        assertThat(executor.isShutdown()).isTrue();
        verify(crawlerService, atLeast(3)).trySubmitScheduled(any());
    }

    @Test
    void executorSchedulerSkipsWhenJobRunning() {
        when(crawlerService.trySubmitScheduled(any())).thenReturn(false);
        CrawlScheduler scheduler = new CrawlScheduler(crawlerService, seedService, executor, props);
        scheduler.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> scheduler.skipped() >= 3);
        assertThat(scheduler.runs()).isZero();
        scheduler.stop();
    }

    @Test
    void overlappingTickIsSkipped() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(seedService.all()).thenAnswer(inv -> {
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of(URI.create("https://a.ru/"));
        });
        when(crawlerService.trySubmitScheduled(any())).thenReturn(true);
        CrawlScheduler scheduler = new CrawlScheduler(crawlerService, seedService, executor, props);
        CompletableFuture<Void> longTick = CompletableFuture.runAsync(scheduler::tick);
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.tick(); // наложение
        assertThat(scheduler.skipped()).isEqualTo(1);
        release.countDown();
        longTick.get(5, TimeUnit.SECONDS);
        assertThat(scheduler.runs()).isEqualTo(1);

        SpringCrawlScheduler spring = new SpringCrawlScheduler(crawlerService, seedService, props);
        CountDownLatch inside2 = new CountDownLatch(1);
        CountDownLatch release2 = new CountDownLatch(1);
        when(seedService.all()).thenAnswer(inv -> {
            inside2.countDown();
            release2.await(5, TimeUnit.SECONDS);
            return List.of(URI.create("https://a.ru/"));
        });
        CompletableFuture<Void> longTick2 = CompletableFuture.runAsync(spring::tick);
        assertThat(inside2.await(5, TimeUnit.SECONDS)).isTrue();
        spring.tick();
        assertThat(spring.skipped()).isEqualTo(1);
        release2.countDown();
        longTick2.get(5, TimeUnit.SECONDS);
        assertThat(spring.runs()).isEqualTo(1);
    }

    @Test
    void noSeedsAndExceptionsAreSkipped() {
        when(seedService.all()).thenReturn(List.of());
        CrawlScheduler scheduler = new CrawlScheduler(crawlerService, seedService, executor, props);
        scheduler.tick();
        assertThat(scheduler.skipped()).isEqualTo(1);
        when(seedService.all()).thenThrow(new IllegalStateException("x"));
        scheduler.tick();
        SpringCrawlScheduler spring = new SpringCrawlScheduler(crawlerService, seedService, props);
        spring.tick();
        assertThat(spring.runs()).isZero();
    }

    @Test
    void seedServiceMergesSourcesAndPersistsApiSeeds() throws Exception {
        Path seedsFile = tmp.resolve("seeds.txt");
        Files.writeString(seedsFile, "# comment\nhttps://file.ru/\n\nnot-a-url\nhttps://dup.ru/\n");
        props.setSeeds(List.of("https://config.ru/", "https://dup.ru/", "ftp://bad"));
        props.setSeedsFile(seedsFile.toString());
        props.setSeedsApiFile(tmp.resolve("data/seeds-api.txt").toString());
        SeedService service = new SeedService(props);
        service.load();
        assertThat(service.all()).extracting(URI::toString)
                .containsExactly("https://config.ru/", "https://dup.ru/", "https://file.ru/");
        service.add(List.of("https://api.ru/x", "https://api.ru/x"));
        assertThatThrownBy(() -> service.add(List.of("ftp://x"))).isInstanceOf(IllegalArgumentException.class);

        SeedService restarted = new SeedService(props); // Q-8: переживает рестарт
        restarted.load();
        assertThat(restarted.all()).extracting(URI::toString).contains("https://api.ru/x");
        restarted.clearApiSeeds();
        assertThat(restarted.all()).extracting(URI::toString).doesNotContain("https://api.ru/x");
        assertThat(Files.exists(tmp.resolve("data/seeds-api.txt"))).isFalse();
    }
}
