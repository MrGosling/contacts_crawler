package com.example.contactscrawler.crawler;

/**
 * Загружает и кеширует robots.txt на хост в рамках задания (ConcurrentHashMap<String, RobotsRules>, computeIfAbsent).
 * 4xx -> всё разрешено; 5xx/таймаут -> хост запрещён. Учитывает User-agent: * и наш UA, Disallow/Allow, Crawl-delay.
 * Методы: boolean isAllowed(URI url), Optional<Duration> crawlDelay(String host), void reset().
 */
public class RobotsTxtChecker {
    // TODO: поля и методы см. Javadoc
}
