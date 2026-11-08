package com.example.contactscrawler.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * Демон-поток {@code storage-flusher}: каждые flush-interval вызывает flushAction. stop(): interrupt + join +
 * финальный flush (D-17).
 */
public class FileFlushDaemon {

    private static final Logger log = LoggerFactory.getLogger(FileFlushDaemon.class);

    private final Runnable flushAction;
    private final long intervalNanos;
    private volatile boolean running;
    private Thread thread;

    public FileFlushDaemon(Runnable flushAction, Duration interval) {
        this.flushAction = flushAction;
        this.intervalNanos = interval.toNanos();
    }

    public synchronized void start() {
        if (thread != null) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "storage-flusher");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(this, intervalNanos);
            if (!running || Thread.currentThread().isInterrupted()) {
                break;
            }
            safeFlush();
        }
    }

    private void safeFlush() {
        try {
            flushAction.run();
        } catch (RuntimeException e) {
            log.warn("Periodic flush failed: {}", e.toString());
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        safeFlush();
    }

    public synchronized boolean isAlive() {
        return thread != null && thread.isAlive();
    }

    public synchronized boolean isDaemon() {
        return thread != null && thread.isDaemon();
    }
}
