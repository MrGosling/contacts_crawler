package com.example.contactscrawler.crawler;

import com.example.contactscrawler.api.dto.CrawlJobResponse;
import com.example.contactscrawler.api.dto.CrawlerStatusResponse;
import com.example.contactscrawler.crawler.engine.CrawlEngine;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Оркестратор. Одно активное задание, остальные QUEUED (D-8, see Q-3). Поток-диспетчер {@code crawl-dispatcher}
 * забирает задания из BlockingQueue (poll с таймаутом) и выполняет их выбранным CrawlEngine.
 * {@code outstanding} (поставленные + выполняемое) позволяет атомарно пропустить плановый запуск при занятости.
 */
@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final CrawlJobRegistry registry;
    private final CrawlEngine engine;
    private final RobotsTxtChecker robots;
    private final BlockingQueue<CrawlJob> pendingJobs = new LinkedBlockingQueue<>();
    private final AtomicReference<CrawlJob> current = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger outstanding = new AtomicInteger();
    private Thread dispatcher;

    public CrawlerService(CrawlJobRegistry registry, CrawlEngine engine, RobotsTxtChecker robots) {
        this.registry = registry;
        this.engine = engine;
        this.robots = robots;
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            dispatcher = new Thread(this::dispatchLoop, "crawl-dispatcher");
            dispatcher.start();
            log.info("CrawlerService started, engine={}", engine.name());
        }
    }

    private void dispatchLoop() {
        while (running.get()) {
            CrawlJob job;
            try {
                job = pendingJobs.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (job != null) {
                execute(job);
            }
        }
    }

    private void execute(CrawlJob job) {
        current.set(job);
        try {
            if (!job.markRunning()) {
                return; // отменено, пока стояло в очереди
            }
            registry.persist(job);
            log.info("Job {} started: {}", job.id(), job.params().seeds());
            robots.reset();
            engine.run(job);
            if (job.isCancelled()) {
                log.info("Job {} cancelled", job.id());
            } else if (job.pagesFetched.sum() == 0 && job.pagesFailed.sum() > 0) {
                job.markFailed("No seed URL could be loaded");
            } else {
                job.markDone();
            }
        } catch (Throwable e) {
            log.error("Job {} failed", job.id(), e);
            job.markFailed(e.toString());
        } finally {
            current.set(null);
            outstanding.decrementAndGet();
            registry.persist(job);
            log.info("Job {} finished: {}", job.id(), job.toResponse());
        }
    }

    /** Ручной запуск: всегда ставится в очередь. */
    public CrawlJob submit(CrawlParams params) {
        return submitAndDescribe(params).job();
    }

    /** Задание + снимок его состояния, сделанный до постановки в очередь (статус QUEUED для ответа 202). */
    public record Submitted(CrawlJob job, CrawlJobResponse snapshot) {
    }

    public Submitted submitAndDescribe(CrawlParams params) {
        ensureRunning();
        outstanding.incrementAndGet();
        CrawlJob job = new CrawlJob(params);
        CrawlJobResponse snapshot = job.toResponse();
        enqueue(job);
        return new Submitted(job, snapshot);
    }

    /** Плановый запуск: false (пропуск), если задание выполняется или ожидает. */
    public boolean trySubmitScheduled(CrawlParams params) {
        ensureRunning();
        if (!outstanding.compareAndSet(0, 1)) {
            log.info("Scheduled crawl skipped: another job is queued or running");
            return false;
        }
        enqueue(new CrawlJob(params));
        return true;
    }

    private CrawlJob enqueue(CrawlJob job) {
        registry.register(job);
        pendingJobs.add(job);
        log.info("Job {} queued", job.id());
        return job;
    }

    private void ensureRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Crawler service is stopped");
        }
    }

    public Optional<CrawlJob> find(String id) {
        return registry.get(id);
    }

    public Optional<CrawlJobResponse> jobResponse(String id) {
        return registry.get(id).map(j -> j.toResponse(current.get() == j ? engine.queueSize() : 0));
    }

    public boolean cancel(String id) {
        Optional<CrawlJob> found = registry.get(id);
        if (found.isEmpty() || !found.get().markCancelled()) {
            return false;
        }
        CrawlJob job = found.get();
        if (pendingJobs.remove(job)) {
            outstanding.decrementAndGet();
        } else if (current.get() == job) {
            engine.stop();
        }
        registry.persist(job);
        return true;
    }

    public CrawlerStatusResponse status() {
        CrawlJob cur = current.get();
        return new CrawlerStatusResponse(cur == null ? null : cur.id(), pendingJobs.size(), engine.queueSize(),
                engine.activeThreads(), engine.poolSize(), engine.visitedUrls(), engine.name());
    }

    public boolean isIdle() {
        return outstanding.get() == 0;
    }

    /** Остановка приложения: текущее и ожидающие задания → CANCELLED, очередь URL сбрасывается (see Q-7). */
    @PreDestroy
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("CrawlerService shutting down");
        CrawlJob job;
        while ((job = pendingJobs.poll()) != null) {
            job.markCancelled();
            outstanding.decrementAndGet();
            registry.persist(job);
        }
        CrawlJob cur = current.get();
        if (cur != null) {
            cur.markCancelled();
            engine.stop();
        }
        if (dispatcher != null) {
            dispatcher.interrupt();
            try {
                dispatcher.join(25_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("CrawlerService stopped");
    }
}
