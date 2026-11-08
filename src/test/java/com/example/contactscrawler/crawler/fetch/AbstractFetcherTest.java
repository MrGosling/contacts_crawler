package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.crawler.model.FetchStatus;
import com.example.contactscrawler.support.TestSupport;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Общий контракт PageFetcher для всех реализаций (MockWebServer). */
abstract class AbstractFetcherTest {

    protected MockWebServer server;
    protected CrawlerProperties props;
    protected PageFetcher fetcher;

    protected abstract PageFetcher create(CrawlerProperties props);

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        props = TestSupport.props();
        props.setMaxPageSize(DataSize.ofKilobytes(4));
        props.setReadTimeout(Duration.ofMillis(700));
        fetcher = create(props);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private URI url(String path) {
        return server.url(path).uri();
    }

    private static MockResponse html(String body) {
        return new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body);
    }

    @Test
    void okHtmlWithUserAgent() throws Exception {
        server.enqueue(html("<html><body>Привет</body></html>"));
        FetchResult r = fetcher.fetch(url("/page"));
        assertThat(r.status()).isEqualTo(FetchStatus.OK);
        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(r.body()).contains("Привет");
        assertThat(r.finalUrl()).isEqualTo(url("/page"));
        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req.getHeader("User-Agent")).isEqualTo(props.getUserAgent());
    }

    @Test
    void followsRedirects() {
        server.enqueue(new MockResponse().setResponseCode(301).setHeader("Location", "/step"));
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", url("/final").toString()));
        server.enqueue(html("<p>final</p>"));
        FetchResult r = fetcher.fetch(url("/start"));
        assertThat(r.status()).isEqualTo(FetchStatus.OK);
        assertThat(r.finalUrl()).isEqualTo(url("/final"));
        assertThat(r.requestedUrl()).isEqualTo(url("/start"));
    }

    @Test
    void tooManyRedirects() {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/loop" + i));
        }
        FetchResult r = fetcher.fetch(url("/loop"));
        assertThat(r.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(r.error()).contains("redirects");
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void notHtml() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"));
        assertThat(fetcher.fetch(url("/doc")).status()).isEqualTo(FetchStatus.NOT_HTML);
    }

    @Test
    void xhtmlIsHtml() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/xhtml+xml").setBody("<p>x</p>"));
        assertThat(fetcher.fetch(url("/x")).status()).isEqualTo(FetchStatus.OK);
    }

    @Test
    void tooLargeWithContentLength() {
        server.enqueue(html("x".repeat(10_000)));
        assertThat(fetcher.fetch(url("/big")).status()).isEqualTo(FetchStatus.TOO_LARGE);
    }

    @Test
    void tooLargeChunked() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setChunkedBody("y".repeat(10_000), 512));
        assertThat(fetcher.fetch(url("/big")).status()).isEqualTo(FetchStatus.TOO_LARGE);
    }

    @Test
    void httpErrors() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(503));
        FetchResult notFound = fetcher.fetch(url("/404"));
        FetchResult unavailable = fetcher.fetch(url("/503"));
        assertThat(notFound.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(notFound.httpStatus()).isEqualTo(404);
        assertThat(unavailable.httpStatus()).isEqualTo(503);
    }

    @Test
    void readTimeoutGivesStatusZero() {
        server.enqueue(html("late").setHeadersDelay(3, TimeUnit.SECONDS));
        FetchResult r = fetcher.fetch(url("/slow"));
        assertThat(r.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(r.httpStatus()).isZero();
    }

    @Test
    void charsetFromHeaderAndMeta() {
        Charset cp1251 = Charset.forName("windows-1251");
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html; charset=windows-1251")
                .setBody(new Buffer().write("<p>Кириллица</p>".getBytes(cp1251))));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                .setBody(new Buffer().write("<meta charset=\"windows-1251\"><p>Мета</p>".getBytes(cp1251))));
        assertThat(fetcher.fetch(url("/h")).body()).contains("Кириллица");
        assertThat(fetcher.fetch(url("/m")).body()).contains("Мета");
    }

    @Test
    void fetchTextAcceptsAnyContentType() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("User-agent: *"));
        FetchResult r = fetcher.fetchText(url("/robots.txt"));
        assertThat(r.status()).isEqualTo(FetchStatus.OK);
        assertThat(r.body()).isEqualTo("User-agent: *");
    }

    @Test
    void connectionRefused() throws IOException {
        URI dead = url("/x");
        server.shutdown();
        FetchResult r = fetcher.fetch(dead);
        assertThat(r.status()).isEqualTo(FetchStatus.FAILED);
        assertThat(r.httpStatus()).isZero();
        server = new MockWebServer();
    }
}
