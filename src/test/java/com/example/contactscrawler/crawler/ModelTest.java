package com.example.contactscrawler.crawler;

import com.example.contactscrawler.api.dto.CrawlRequest;
import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.crawler.model.JobStatus;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelTest {

    private final CrawlerProperties props = new CrawlerProperties();

    @Test
    void crawlParamsDefaultsAndOverrides() {
        CrawlParams p = CrawlParams.of(new CrawlRequest(List.of("https://a.ru"), null, null, null, null), props);
        assertThat(p.maxDepth()).isEqualTo(2);
        assertThat(p.maxPagesPerDomain()).isEqualTo(100);
        assertThat(p.sameDomainOnly()).isTrue();
        assertThat(p.delayMs()).isEqualTo(1000);
        CrawlParams o = CrawlParams.of(new CrawlRequest(List.of("http://b.ru/x"), 0, 5, false, 2000L), props);
        assertThat(o.maxDepth()).isZero();
        assertThat(o.maxPagesPerDomain()).isEqualTo(5);
        assertThat(o.sameDomainOnly()).isFalse();
        assertThat(o.delayMs()).isEqualTo(2000);
        assertThat(CrawlParams.defaults(List.of(URI.create("https://a.ru")), props).delayMs()).isEqualTo(1000);
    }

    @Test
    void crawlParamsValidation() {
        assertThatThrownBy(() -> CrawlParams.of(new CrawlRequest(List.of("https://a.ru"), null, null, null, 10L), props))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("delayMs");
        assertThatThrownBy(() -> CrawlParams.parseHttpUri("ftp://a.ru")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlParams.parseHttpUri("http://bad host")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrawlParams(List.of(), 1, 1, true, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrawlParams(List.of(URI.create("https://a.ru")), 6, 1, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrawlParams(List.of(URI.create("https://a.ru")), 1, 0, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrawlParams(List.of(URI.create("https://a.ru")), 1, 1, true, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobTransitions() throws InterruptedException {
        CrawlJob job = new CrawlJob(new CrawlParams(List.of(URI.create("https://a.ru")), 1, 1, true, 0));
        assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.markDone()).isFalse();
        assertThat(job.markRunning()).isTrue();
        assertThat(job.markRunning()).isFalse();
        assertThat(job.await(Duration.ofMillis(10))).isFalse();
        assertThat(job.markDone()).isTrue();
        assertThat(job.isTerminal()).isTrue();
        assertThat(job.markCancelled()).isFalse();
        assertThat(job.markFailed("x")).isFalse();
        assertThat(job.await(Duration.ofMillis(10))).isTrue();
        assertThat(job.toResponse().status()).isEqualTo(JobStatus.DONE);
        assertThat(job.toString()).contains(job.id());

        CrawlJob queued = new CrawlJob(job.params());
        assertThat(queued.markFailed("err")).isTrue();
        assertThat(queued.error()).isEqualTo("err");
        CrawlJob cancelled = new CrawlJob(job.params());
        assertThat(cancelled.markCancelled()).isTrue();
        assertThat(cancelled.isCancelled()).isTrue();
    }

    @Test
    void contactMergeKeepsFirstSourceAndMaxLastSeen() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
        Contact first = new Contact(1L, ContactType.PHONE, null, "v", "n", "a.ru", "https://a.ru/1", t1, t1, "j1");
        Contact later = new Contact(null, ContactType.PHONE, null, "v", "n", "a.ru", "https://a.ru/2", t2, t2, "j2");
        Contact merged = first.mergeWith(later);
        assertThat(merged.sourceUrl()).isEqualTo("https://a.ru/1");
        assertThat(merged.foundAt()).isEqualTo(t1);
        assertThat(merged.lastSeenAt()).isEqualTo(t2);
        assertThat(later.mergeWith(first).lastSeenAt()).isEqualTo(t2);
        assertThat(first.withJobId("x").jobId()).isEqualTo("x");
    }
}
