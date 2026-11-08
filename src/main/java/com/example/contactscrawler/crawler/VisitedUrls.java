package com.example.contactscrawler.crawler;

/**
 * Потокобезопасное множество посещённых URL: ConcurrentHashMap.newKeySet() + ConcurrentHashMap<String, AtomicInteger> pagesPerDomain.
 * Методы: boolean markIfNew(URI) (атомарно, true один раз), boolean tryReserveDomainSlot(String domain, int limit), int size(), void clear().
 */
public class VisitedUrls {
    // TODO: поля и методы см. Javadoc
}
