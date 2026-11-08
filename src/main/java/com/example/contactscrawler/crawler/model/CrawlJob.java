package com.example.contactscrawler.crawler.model;

/**
 * Состояние задания (потокобезопасное): String id (UUID), CrawlParams params, AtomicReference<JobStatus> status,
 * Instant createdAt/startedAt/finishedAt (volatile), LongAdder pagesFetched/pagesSkipped/pagesFailed/contactsFound,
 * volatile String error, CountDownLatch done (1).
 * Методы: markRunning(), markDone(), markFailed(String), markCancelled() (переходы через compareAndSet),
 * boolean await(Duration), CrawlJobResponse toResponse().
 */
public class CrawlJob {
    // TODO: поля и методы см. Javadoc
}
