package com.example.contactscrawler.crawler.model;

import com.example.contactscrawler.api.dto.CrawlJobResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Потокобезопасное состояние задания. Переходы статуса — через compareAndSet; терминальный статус
 * освобождает {@link CountDownLatch} {@code done}.
 */
public class CrawlJob {

    private final String id;
    private final CrawlParams params;
    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.QUEUED);
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String error;
    private final CountDownLatch done = new CountDownLatch(1);

    public final LongAdder pagesFetched = new LongAdder();
    public final LongAdder pagesSkipped = new LongAdder();
    public final LongAdder pagesFailed = new LongAdder();
    public final LongAdder contactsFound = new LongAdder();

    public CrawlJob(CrawlParams params) {
        this.id = UUID.randomUUID().toString();
        this.params = params;
        this.createdAt = Instant.now();
    }

    public boolean markRunning() {
        if (status.compareAndSet(JobStatus.QUEUED, JobStatus.RUNNING)) {
            startedAt = Instant.now();
            return true;
        }
        return false;
    }

    public boolean markDone() {
        return finish(JobStatus.RUNNING, JobStatus.DONE);
    }

    public boolean markFailed(String message) {
        error = message;
        return finish(JobStatus.RUNNING, JobStatus.FAILED) || finish(JobStatus.QUEUED, JobStatus.FAILED);
    }

    /** QUEUED|RUNNING → CANCELLED. */
    public boolean markCancelled() {
        return finish(JobStatus.RUNNING, JobStatus.CANCELLED) || finish(JobStatus.QUEUED, JobStatus.CANCELLED);
    }

    private boolean finish(JobStatus from, JobStatus to) {
        if (status.compareAndSet(from, to)) {
            finishedAt = Instant.now();
            done.countDown();
            return true;
        }
        return false;
    }

    public boolean await(Duration timeout) throws InterruptedException {
        return done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isCancelled() {
        return status.get() == JobStatus.CANCELLED;
    }

    public boolean isTerminal() {
        JobStatus s = status.get();
        return s == JobStatus.DONE || s == JobStatus.FAILED || s == JobStatus.CANCELLED;
    }

    public CrawlJobResponse toResponse() {
        return toResponse(0);
    }

    public CrawlJobResponse toResponse(int queueSize) {
        return new CrawlJobResponse(id, status.get(), createdAt, startedAt, finishedAt, pagesFetched.sum(),
                pagesSkipped.sum(), pagesFailed.sum(), contactsFound.sum(), queueSize, error);
    }

    public String id() { return id; }
    public CrawlParams params() { return params; }
    public JobStatus status() { return status.get(); }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String error() { return error; }

    @Override
    public String toString() {
        return "CrawlJob[" + id + ", " + status.get() + "]";
    }
}
