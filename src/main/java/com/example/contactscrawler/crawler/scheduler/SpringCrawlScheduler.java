package com.example.contactscrawler.crawler.scheduler;

/**
 * Вариант на @Scheduled(fixedDelayString = "${crawler.scheduler.fixed-delay}") (crawler.scheduler.mode=spring).
 * Защита от наложения: ReentrantLock.tryLock(). Методы: void tick().
 */
public class SpringCrawlScheduler {
    // TODO: поля и методы см. Javadoc
}
