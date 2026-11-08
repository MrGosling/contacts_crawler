package com.example.contactscrawler.crawler;

/**
 * Ограничение запросов к хосту: ConcurrentHashMap<String, HostSlot>, где HostSlot = Semaphore(perHostConcurrency) +
 * AtomicLong lastRequestAt. Пауза max(delayMs, Crawl-delay) между запросами к одному хосту.
 * Методы: Permit acquire(String host, Duration delay) throws InterruptedException (AutoCloseable, release в close).
 */
public class HostRateLimiter {
    // TODO: поля и методы см. Javadoc
}
