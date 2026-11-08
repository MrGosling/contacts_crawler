package com.example.contactscrawler.crawler;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Потокобезопасное множество посещённых URL (ConcurrentHashMap.newKeySet()) + счётчики страниц на домен.
 * URL помечается атомарно при постановке в очередь (п.8 03-crawling.md).
 */
public class VisitedUrls {

    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, AtomicInteger> pagesPerDomain = new ConcurrentHashMap<>();

    /** true ровно один раз для каждого URL. */
    public boolean markIfNew(URI url) {
        return visited.add(url.toString());
    }

    public boolean tryReserveDomainSlot(String domain, int limit) {
        AtomicInteger counter = pagesPerDomain.computeIfAbsent(domain, d -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public int pagesFor(String domain) {
        AtomicInteger c = pagesPerDomain.get(domain);
        return c == null ? 0 : c.get();
    }

    public int size() {
        return visited.size();
    }

    public void clear() {
        visited.clear();
        pagesPerDomain.clear();
    }
}
