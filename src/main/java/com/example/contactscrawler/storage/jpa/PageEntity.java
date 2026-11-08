package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.crawler.model.FetchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Таблица page: id, job_id, url, domain, depth, http_status, fetch_status, fetched_at.
 */
@Entity
@Table(name = "page", indexes = @Index(name = "idx_page_job", columnList = "job_id"))
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(length = 2048)
    private String url;

    @Column(length = 255)
    private String domain;

    private int depth;

    @Column(name = "http_status")
    private int httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", length = 16)
    private FetchStatus fetchStatus;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    protected PageEntity() {
    }

    public PageEntity(String jobId, String url, String domain, int depth, int httpStatus, FetchStatus fetchStatus,
                      Instant fetchedAt) {
        this.jobId = jobId;
        this.url = ContactEntity.truncate(url, 2048);
        this.domain = domain;
        this.depth = depth;
        this.httpStatus = httpStatus;
        this.fetchStatus = fetchStatus;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public String getUrl() { return url; }
    public FetchStatus getFetchStatus() { return fetchStatus; }
}
