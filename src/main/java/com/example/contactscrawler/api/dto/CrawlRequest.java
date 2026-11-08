package com.example.contactscrawler.api.dto;

/**
 * DTO запроса POST /api/crawl: @NotEmpty @Size(max=100) List<@URL String> urls; Integer maxDepth (0..5),
 * Integer maxPagesPerDomain (1..10000), Boolean sameDomainOnly, Long delayMs (>=1000, кроме test). null -> значения из CrawlerProperties.
 */
public class CrawlRequest {
    // TODO: поля и методы см. Javadoc
}
