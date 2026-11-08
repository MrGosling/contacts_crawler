package com.example.contactscrawler.crawler.model;

import java.net.URI;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Единица работы. Порядок: приоритет desc → depth asc → sequence asc (FIFO).
 */
public record CrawlTask(URI url, int depth, int priority, long sequence, String jobId) implements Comparable<CrawlTask> {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private static final Comparator<CrawlTask> ORDER = Comparator
            .comparingInt(CrawlTask::priority).reversed()
            .thenComparingInt(CrawlTask::depth)
            .thenComparingLong(CrawlTask::sequence);

    public static CrawlTask of(URI url, int depth, int priority, String jobId) {
        return new CrawlTask(url, depth, priority, SEQUENCE.incrementAndGet(), jobId);
    }

    @Override
    public int compareTo(CrawlTask o) {
        return ORDER.compare(this, o);
    }
}
