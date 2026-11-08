package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Таблица crawl_job: id (UUID), status, параметры, счётчики, created_at, started_at, finished_at, error.
 */
@Entity
@Table(name = "crawl_job")
public class CrawlJobEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(length = 8000)
    private String seeds;

    @Column(name = "max_depth")
    private int maxDepth;

    @Column(name = "max_pages_per_domain")
    private int maxPagesPerDomain;

    @Column(name = "same_domain_only")
    private boolean sameDomainOnly;

    @Column(name = "delay_ms")
    private long delayMs;

    @Column(name = "pages_fetched")
    private long pagesFetched;

    @Column(name = "pages_skipped")
    private long pagesSkipped;

    @Column(name = "pages_failed")
    private long pagesFailed;

    @Column(name = "contacts_found")
    private long contactsFound;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 2000)
    private String error;

    protected CrawlJobEntity() {
    }

    public static CrawlJobEntity from(CrawlJob job) {
        CrawlJobEntity e = new CrawlJobEntity();
        e.id = job.id();
        e.status = job.status();
        e.seeds = ContactEntity.truncate(job.params().seeds().stream().map(URI::toString)
                .collect(Collectors.joining("\n")), 8000);
        e.maxDepth = job.params().maxDepth();
        e.maxPagesPerDomain = job.params().maxPagesPerDomain();
        e.sameDomainOnly = job.params().sameDomainOnly();
        e.delayMs = job.params().delayMs();
        e.pagesFetched = job.pagesFetched.sum();
        e.pagesSkipped = job.pagesSkipped.sum();
        e.pagesFailed = job.pagesFailed.sum();
        e.contactsFound = job.contactsFound.sum();
        e.createdAt = job.createdAt();
        e.startedAt = job.startedAt();
        e.finishedAt = job.finishedAt();
        e.error = ContactEntity.truncate(job.error(), 2000);
        return e;
    }

    public String getId() { return id; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getPagesFetched() { return pagesFetched; }
}
