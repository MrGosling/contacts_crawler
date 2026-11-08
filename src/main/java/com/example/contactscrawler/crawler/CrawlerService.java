package com.example.contactscrawler.crawler;

/**
 * Оркестратор. Принимает задания, держит очередь заданий (одно активное, остальные QUEUED — D-8/Q-3), выбирает CrawlEngine.
 * Поля: CrawlJobRegistry registry, CrawlEngine engine, BlockingQueue<CrawlJob> pendingJobs, AtomicReference<CrawlJob> current,
 * AtomicBoolean running, поток-диспетчер заданий.
 * Методы: CrawlJob submit(CrawlParams), boolean trySubmitScheduled(CrawlParams) (false если занято — пропуск планового запуска),
 * Optional<CrawlJob> find(String id), boolean cancel(String id), CrawlerStatusResponse status(),
 * @PreDestroy shutdown() — отмена текущего, CANCELLED для всех.
 */
public class CrawlerService {
    // TODO: поля и методы см. Javadoc
}
