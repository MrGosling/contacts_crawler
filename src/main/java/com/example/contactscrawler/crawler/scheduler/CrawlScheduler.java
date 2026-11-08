package com.example.contactscrawler.crawler.scheduler;

/**
 * Планировщик на ScheduledExecutorService (crawler.scheduler.mode=executor). scheduleWithFixedDelay(tick, initialDelay, fixedDelay).
 * Поля: ScheduledExecutorService, AtomicBoolean inProgress, ScheduledFuture<?> handle, AtomicLong runs/skipped.
 * Методы: start() (@PostConstruct), tick() — compareAndSet(false,true) иначе пропуск с логом; CrawlerService.trySubmitScheduled(seeds),
 * stop() (@PreDestroy) — cancel + shutdown/awaitTermination.
 */
public class CrawlScheduler {
    // TODO: поля и методы см. Javadoc
}
