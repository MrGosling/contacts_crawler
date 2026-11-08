package com.example.contactscrawler.api.dto;

import com.example.contactscrawler.crawler.model.JobStatus;

import java.time.Instant;

/**
 * DTO статуса задания.
 */
public record CrawlJobResponse(String jobId, JobStatus status, Instant createdAt, Instant startedAt,
                               Instant finishedAt, long pagesFetched, long pagesSkipped, long pagesFailed,
                               long contactsFound, int queueSize, String error) {
}
