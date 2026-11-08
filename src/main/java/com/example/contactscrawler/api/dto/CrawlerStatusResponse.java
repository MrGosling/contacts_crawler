package com.example.contactscrawler.api.dto;

/**
 * DTO общего статуса краулера.
 */
public record CrawlerStatusResponse(String currentJobId, int queuedJobs, int queueSize, int activeThreads,
                                    int poolSize, int visitedUrls, String engine) {
}
