package com.example.contactscrawler.api;

/**
 * @RestController /api/crawl.
 * POST /api/crawl (CrawlRequest) -> 202 + CrawlJobResponse + Location;
 * GET /api/crawl/{jobId} -> CrawlJobResponse | 404; DELETE /api/crawl/{jobId} -> 202 (отмена);
 * GET /api/crawl/status -> CrawlerStatusResponse. Зависимости: CrawlerService.
 */
public class CrawlController {
    // TODO: поля и методы см. Javadoc
}
