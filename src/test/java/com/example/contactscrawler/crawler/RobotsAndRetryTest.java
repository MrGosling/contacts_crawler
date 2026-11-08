package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.crawler.model.FetchStatus;
import com.example.contactscrawler.support.TestSupport;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsAndRetryTest {

    private MockWebServer server;
    private CrawlerProperties props;
    private PageFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        props = TestSupport.props();
        fetcher = TestSupport.webClientFetcher(props);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void parseRulesLongestMatchWins() {
        String robots = """
                # comment
                User-agent: Googlebot
                Disallow: /

                User-agent: *
                Disallow: /private
                Allow: /private/public
                Disallow: /*.php$
                Crawl-delay: 2.5
                """;
        RobotsTxtChecker.RobotsRules rules = RobotsTxtChecker.parse(robots, "contactscrawler");
        assertThat(rules.isAllowed("/")).isTrue();
        assertThat(rules.isAllowed("/private/x")).isFalse();
        assertThat(rules.isAllowed("/private/public/y")).isTrue();
        assertThat(rules.isAllowed("/index.php")).isFalse();
        assertThat(rules.isAllowed("/index.php?x=1")).isTrue();
        assertThat(rules.crawlDelay()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    void specificAgentGroupPreferred() {
        String robots = "User-agent: *\nDisallow: /\n\nUser-agent: ContactsCrawler\nUser-agent: other\nDisallow: /admin\nDisallow:\n";
        RobotsTxtChecker.RobotsRules rules = RobotsTxtChecker.parse(robots, "contactscrawler");
        assertThat(rules.isAllowed("/page")).isTrue();
        assertThat(rules.isAllowed("/admin")).isFalse();
        assertThat(RobotsTxtChecker.parse("garbage\nCrawl-delay: x", "a").isAllowed("/x")).isTrue();
    }

    private void robotsResponse(MockResponse response, AtomicInteger hits) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                hits.incrementAndGet();
                return response;
            }
        });
    }

    @Test
    void robots404AllowsAll500DeniesAll() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        robotsResponse(new MockResponse().setResponseCode(404), hits);
        RobotsTxtChecker checker = new RobotsTxtChecker(fetcher, props);
        assertThat(checker.isAllowed(server.url("/anything").uri())).isTrue();
        assertThat(checker.crawlDelay(server.url("/").uri())).isEmpty();

        robotsResponse(new MockResponse().setResponseCode(500), hits);
        checker.reset();
        assertThat(checker.isAllowed(server.url("/anything").uri())).isFalse();
    }

    @RepeatedTest(5)
    void robotsLoadedOncePerHostUnderConcurrency() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        robotsResponse(new MockResponse().setHeader("Content-Type", "text/plain")
                .setBody("User-agent: *\nDisallow: /private\nCrawl-delay: 1"), hits);
        RobotsTxtChecker checker = new RobotsTxtChecker(fetcher, props);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            String path = i % 2 == 0 ? "/private/" + i : "/open/" + i;
            results.add(CompletableFuture.supplyAsync(() -> checker.isAllowed(server.url(path).uri()), pool));
        }
        long allowed = results.stream().filter(CompletableFuture::join).count();
        pool.shutdown();
        assertThat(allowed).isEqualTo(16);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(checker.crawlDelay(server.url("/").uri())).contains(Duration.ofSeconds(1));
    }

    @Test
    void retriesOnlyRetryableErrors() throws Exception {
        RetryPolicy retry = new RetryPolicy(props);
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("ok"));
        URI u = server.url("/r").uri();
        assertThat(retry.execute(() -> fetcher.fetch(u)).status()).isEqualTo(FetchStatus.OK);
        assertThat(server.getRequestCount()).isEqualTo(3);

        server.enqueue(new MockResponse().setResponseCode(404));
        assertThat(retry.execute(() -> fetcher.fetch(u)).httpStatus()).isEqualTo(404);
        assertThat(server.getRequestCount()).isEqualTo(4);

        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(502));
        }
        assertThat(retry.execute(() -> fetcher.fetch(u)).httpStatus()).isEqualTo(502);
        assertThat(server.getRequestCount()).isEqualTo(7);
    }

    @Test
    void isRetryable() {
        RetryPolicy retry = new RetryPolicy(2, Duration.ofMillis(1));
        URI u = URI.create("https://a.ru");
        assertThat(retry.isRetryable(FetchResult.failure(u, u, 0, FetchStatus.FAILED, null, Duration.ZERO, "t"))).isTrue();
        assertThat(retry.isRetryable(FetchResult.failure(u, u, 503, FetchStatus.FAILED, null, Duration.ZERO, "t"))).isTrue();
        assertThat(retry.isRetryable(FetchResult.failure(u, u, 403, FetchStatus.FAILED, null, Duration.ZERO, "t"))).isFalse();
        assertThat(retry.isRetryable(FetchResult.failure(u, u, 200, FetchStatus.TOO_LARGE, null, Duration.ZERO, "t"))).isFalse();
        assertThat(retry.isRetryable(FetchResult.skippedByRobots(u))).isFalse();
    }
}
