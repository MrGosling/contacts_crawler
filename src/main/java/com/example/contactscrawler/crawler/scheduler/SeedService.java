package com.example.contactscrawler.crawler.scheduler;

/**
 * Источник seed-URL: crawler.seeds + seeds.txt (read-only) + seeds из REST (CopyOnWriteArrayList, персист в data/seeds-api.txt — Q-8).
 * Методы: List<URI> all(), List<URI> add(List<String>), void clearApiSeeds(), @PostConstruct load().
 */
public class SeedService {
    // TODO: поля и методы см. Javadoc
}
