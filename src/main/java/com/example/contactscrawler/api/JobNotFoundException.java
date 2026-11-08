package com.example.contactscrawler.api;

/**
 * Задание с jobId не найдено (→ 404).
 */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Crawl job not found: " + jobId);
    }
}
