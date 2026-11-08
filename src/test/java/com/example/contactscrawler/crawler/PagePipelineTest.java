package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.crawler.model.CrawlTask;
import com.example.contactscrawler.crawler.model.FetchStatus;
import com.example.contactscrawler.extract.ExtractionResult;
import com.example.contactscrawler.logging.AsyncLogger;
import com.example.contactscrawler.storage.InMemoryContactRepository;
import com.example.contactscrawler.support.TestSupport;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PagePipelineTest {

    @TempDir
    Path tmp;

    private MockWebServer server;
    private InMemoryContactRepository repo;
    private AsyncLogger logger;
    private PagePipeline pipeline;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        CrawlerProperties props = TestSupport.props();
        repo = new InMemoryContactRepository();
        logger = TestSupport.asyncLogger(tmp);
        pipeline = TestSupport.pipeline(props, repo, logger);
    }

    @AfterEach
    void tearDown() throws IOException {
        logger.close();
        server.shutdown();
    }

    private CrawlJob job(int depth) {
        return new CrawlJob(new CrawlParams(List.of(server.url("/").uri()), depth, 100, true, 0));
    }

    @Test
    void redirectToForeignDomainIsSkipped() {
        server.enqueue(new MockResponse().setResponseCode(404)); // robots.txt
        String foreign = "http://127.0.0.1:" + server.getPort() + "/other";
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", foreign));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<a href='mailto:a@b.ru'>m</a>"));
        CrawlJob job = job(1);
        CrawlTask task = CrawlTask.of(server.url("/start").uri(), 0, 0, job.id());
        assertThat(pipeline.process(task, job, new VisitedUrls())).isEmpty();
        assertThat(job.pagesSkipped.sum()).isEqualTo(1);
        assertThat(repo.count()).isZero();
    }

    @Test
    void storesContactsAndOrganizationAndDiscoversLinks() {
        server.enqueue(new MockResponse().setResponseCode(404)); // robots.txt
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody(
                "<html><head><meta property='og:site_name' content='Org'></head><body>"
                        + "<a href='mailto:info@org.ru'>m</a><a href='/kontakty'>k</a><a href='/a'>a</a></body></html>"));
        CrawlJob job = job(1);
        VisitedUrls visited = new VisitedUrls();
        List<CrawlTask> seeds = pipeline.seedTasks(job, visited);
        assertThat(seeds).hasSize(1);
        List<CrawlTask> next = pipeline.process(seeds.get(0), job, visited);
        assertThat(next).extracting(t -> t.url().getPath()).containsExactlyInAnyOrder("/kontakty", "/a");
        assertThat(next).filteredOn(t -> t.url().getPath().equals("/kontakty"))
                .allSatisfy(t -> assertThat(t.priority()).isEqualTo(UrlFilter.HIGH_PRIORITY));
        assertThat(repo.count()).isEqualTo(2); // email + website
        assertThat(repo.organizationNames()).containsEntry("localhost", "Org");
        assertThat(job.contactsFound.sum()).isEqualTo(2);
        // на максимальной глубине ссылки не извлекаются
        assertThat(pipeline.discover(null, next.get(0), job, visited)).isEmpty();
    }

    @Test
    void robotsDisallowedPageIsSkipped() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("User-agent: *\nDisallow: /"));
        CrawlJob job = job(1);
        CrawlTask task = CrawlTask.of(server.url("/x").uri(), 0, 0, job.id());
        assertThat(pipeline.fetch(task, job).status()).isEqualTo(FetchStatus.SKIPPED_ROBOTS);
        assertThat(job.pagesSkipped.sum()).isEqualTo(1);
    }

    @Test
    void failedPageCounted() {
        server.enqueue(new MockResponse().setResponseCode(404)); // robots
        server.enqueue(new MockResponse().setResponseCode(404));
        CrawlJob job = job(1);
        CrawlTask task = CrawlTask.of(server.url("/missing").uri(), 0, 0, job.id());
        assertThat(pipeline.process(task, job, new VisitedUrls())).isEmpty();
        assertThat(job.pagesFailed.sum()).isEqualTo(1);
    }

    @Test
    void storeWithoutContacts() {
        CrawlJob job = job(1);
        pipeline.store(new ExtractionResult(List.of(), Optional.empty()), URI.create("https://a.ru/"), job);
        assertThat(repo.count()).isZero();
        assertThat(Jsoup.parse("<p/>")).isNotNull();
    }
}
