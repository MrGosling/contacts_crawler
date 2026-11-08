package com.example.contactscrawler.logging;

import com.example.contactscrawler.config.CrawlerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Асинхронный логгер: ограниченная LinkedBlockingQueue&lt;LogEvent&gt; + демон-поток {@code async-logger},
 * пишет в crawler.async-log.path через BufferedWriter. Порядок сохраняется (один потребитель, FIFO).
 * close(): POISON → join → дренаж остатка → flush (демон не гарантирует дописывание при выходе JVM — D-17).
 */
@Component
public class AsyncLogger implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncLogger.class);

    private final Path path;
    private final BlockingQueue<LogEvent> queue;
    private final LongAdder dropped = new LongAdder();
    private final LongAdder written = new LongAdder();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private BufferedWriter writer;
    private Thread worker;

    @Autowired
    public AsyncLogger(CrawlerProperties props) {
        this(Path.of(props.getAsyncLog().getPath()), props.getAsyncLog().getCapacity());
    }

    public AsyncLogger(Path path, int capacity) {
        this.path = path;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open async log " + path, e);
        }
        worker = new Thread(this::loop, "async-logger");
        worker.setDaemon(true);
        worker.start();
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    /** Неблокирующая запись; при переполнении или после close() событие теряется (учитывается в droppedCount). */
    public void log(String level, String message) {
        if (closed.get() || !queue.offer(LogEvent.of(level, message))) {
            dropped.increment();
        }
    }

    private void loop() {
        try {
            while (true) {
                LogEvent e = queue.poll(200, TimeUnit.MILLISECONDS);
                if (e == null) {
                    flushWriter();
                    continue;
                }
                if (e == LogEvent.POISON) {
                    break;
                }
                write(e);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            drain();
        }
    }

    private synchronized void write(LogEvent e) {
        try {
            writer.write(e.format());
            writer.newLine();
            written.increment();
        } catch (IOException ex) {
            log.warn("Async log write failed: {}", ex.toString());
        }
    }

    private synchronized void drain() {
        LogEvent e;
        while ((e = queue.poll()) != null) {
            if (e != LogEvent.POISON) {
                write(e);
            }
        }
        flushWriter();
    }

    private synchronized void flushWriter() {
        try {
            writer.flush();
        } catch (IOException ex) {
            log.warn("Async log flush failed: {}", ex.toString());
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!started.get() || !closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!queue.offer(LogEvent.POISON, 2, TimeUnit.SECONDS)) {
                worker.interrupt();
            }
            worker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drain();
        synchronized (this) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Async log close failed: {}", e.toString());
            }
        }
        log.info("AsyncLogger closed: written={}, dropped={}", written.sum(), dropped.sum());
    }

    public long droppedCount() {
        return dropped.sum();
    }

    public long writtenCount() {
        return written.sum();
    }

    public boolean isWorkerAlive() {
        return worker != null && worker.isAlive();
    }

    public Path path() {
        return path;
    }
}
