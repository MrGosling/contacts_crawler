package com.example.contactscrawler.crawler.engine;

/**
 * Этап 1. implements CrawlEngine. N потоков через new Thread(new CrawlWorker(...), "crawler-thread-N"),
 * общая ConcurrentLinkedQueue<CrawlTask>, VisitedUrls, AtomicInteger activeTasks.
 * Завершение: очередь пуста и activeTasks == 0 -> volatile running=false. stop(): running=false + interrupt() всех, join().
 */
public class ThreadCrawlEngine {
    // TODO: поля и методы см. Javadoc
}
