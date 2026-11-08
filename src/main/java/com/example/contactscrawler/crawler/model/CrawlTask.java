package com.example.contactscrawler.crawler.model;

/**
 * Единица работы: URI url, int depth, int priority, long sequence (FIFO при равенстве), String jobId.
 * implements Comparable<CrawlTask>: приоритет desc -> depth asc -> sequence asc (для PriorityBlockingQueue).
 */
public class CrawlTask {
    // TODO: поля и методы см. Javadoc
}
