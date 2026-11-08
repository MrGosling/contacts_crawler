package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.model.CrawlTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * PriorityBlockingQueue&lt;CrawlTask&gt; с ограничением ёмкости (crawler.queue-capacity) и счётчиком отброшенных.
 * Ёмкость резервируется атомарно (AtomicInteger), поэтому предел не превышается при конкурентных offer.
 */
public class CrawlQueue {

    private final PriorityBlockingQueue<CrawlTask> queue = new PriorityBlockingQueue<>();
    private final int capacity;
    private final AtomicInteger reserved = new AtomicInteger();
    private final LongAdder dropped = new LongAdder();

    public CrawlQueue(int capacity) {
        this.capacity = capacity;
    }

    /** false при переполнении (задача отбрасывается и учитывается в droppedCount). */
    public boolean offer(CrawlTask task) {
        if (reserved.incrementAndGet() > capacity) {
            reserved.decrementAndGet();
            dropped.increment();
            return false;
        }
        queue.offer(task);
        return true;
    }

    /** poll(timeout) вместо бесконечного take(), чтобы потребитель мог завершиться. */
    public CrawlTask poll(Duration timeout) throws InterruptedException {
        CrawlTask t = queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (t != null) {
            reserved.decrementAndGet();
        }
        return t;
    }

    public int size() {
        return queue.size();
    }

    public int clear() {
        List<CrawlTask> drained = new ArrayList<>();
        int n = queue.drainTo(drained);
        reserved.addAndGet(-n);
        return n;
    }

    public long droppedCount() {
        return dropped.sum();
    }
}
