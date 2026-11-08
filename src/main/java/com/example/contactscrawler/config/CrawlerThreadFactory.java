package com.example.contactscrawler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * ThreadFactory: имена {@code <prefix>-N} (AtomicInteger), setDaemon(false), UncaughtExceptionHandler с логированием.
 */
public class CrawlerThreadFactory implements ThreadFactory {

    private static final Logger log = LoggerFactory.getLogger(CrawlerThreadFactory.class);

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger counter = new AtomicInteger();
    private final LongAdder uncaught = new LongAdder();

    public CrawlerThreadFactory() {
        this("crawler-worker", false);
    }

    public CrawlerThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
        t.setDaemon(daemon);
        t.setUncaughtExceptionHandler((thread, e) -> {
            uncaught.increment();
            log.error("Uncaught exception in {}", thread.getName(), e);
        });
        return t;
    }

    public long uncaughtCount() {
        return uncaught.sum();
    }
}
