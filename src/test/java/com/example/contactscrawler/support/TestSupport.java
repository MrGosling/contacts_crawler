package com.example.contactscrawler.support;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.config.HttpClientConfig;
import com.example.contactscrawler.crawler.HostRateLimiter;
import com.example.contactscrawler.crawler.LinkExtractor;
import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.PageRecorder;
import com.example.contactscrawler.crawler.RetryPolicy;
import com.example.contactscrawler.crawler.RobotsTxtChecker;
import com.example.contactscrawler.crawler.UrlFilter;
import com.example.contactscrawler.crawler.UrlNormalizer;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.fetch.WebClientFetcher;
import com.example.contactscrawler.extract.AddressExtractor;
import com.example.contactscrawler.extract.ContactExtractionService;
import com.example.contactscrawler.extract.EmailExtractor;
import com.example.contactscrawler.extract.OpeningHoursExtractor;
import com.example.contactscrawler.extract.OrgNameExtractor;
import com.example.contactscrawler.extract.PhoneExtractor;
import com.example.contactscrawler.extract.SocialLinkExtractor;
import com.example.contactscrawler.extract.StructuredDataReader;
import com.example.contactscrawler.logging.AsyncLogger;
import com.example.contactscrawler.storage.ContactRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Ручная сборка компонентов без Spring-контекста. */
public final class TestSupport {

    private TestSupport() {
    }

    public static CrawlerProperties props() {
        CrawlerProperties p = new CrawlerProperties();
        p.setDelayMs(0);
        p.setMinDelayMs(0);
        p.setRetryBackoff(Duration.ofMillis(10));
        p.setThreads(4);
        p.setConnectTimeout(Duration.ofSeconds(2));
        p.setReadTimeout(Duration.ofSeconds(3));
        return p;
    }

    public static StructuredDataReader reader() {
        return new StructuredDataReader();
    }

    public static ContactExtractionService extraction(CrawlerProperties p) {
        StructuredDataReader r = reader();
        return new ContactExtractionService(List.of(
                new PhoneExtractor(r, p),
                new EmailExtractor(r, p),
                new AddressExtractor(r),
                new SocialLinkExtractor(p),
                new OpeningHoursExtractor(r)), new OrgNameExtractor(r));
    }

    public static PageFetcher webClientFetcher(CrawlerProperties p) {
        return new WebClientFetcher(HttpClientConfig.newWebClient(p), p);
    }

    public static AsyncLogger asyncLogger(Path dir) {
        AsyncLogger logger = new AsyncLogger(dir.resolve("async.log"), 100_000);
        logger.start();
        return logger;
    }

    public static PagePipeline pipeline(CrawlerProperties p, ContactRepository repo, AsyncLogger logger) {
        PageFetcher fetcher = webClientFetcher(p);
        return new PagePipeline(new RobotsTxtChecker(fetcher, p), new HostRateLimiter(p), fetcher, new RetryPolicy(p),
                new LinkExtractor(), new UrlNormalizer(), new UrlFilter(p), extraction(p), repo, logger,
                PageRecorder.NOOP);
    }

    public static Document fixture(String name, String url) {
        try (InputStream in = TestSupport.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), url);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Document html(String body) {
        return Jsoup.parse("<html><head><title>T</title></head><body>" + body + "</body></html>", "https://www.romashka.ru/contacts");
    }

    public static URI page() {
        return URI.create("https://www.romashka.ru/contacts");
    }
}
