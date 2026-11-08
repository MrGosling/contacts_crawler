package com.example.contactscrawler.crawler;

/**
 * Обработка одной задачи, общая для всех движков: robots -> rate limit -> fetch (с повторами) -> Jsoup.parse ->
 * ContactExtractionService -> ContactRepository.saveAll -> LinkExtractor/UrlNormalizer/UrlFilter -> новые CrawlTask.
 * Методы: List<CrawlTask> process(CrawlTask task, CrawlJob job, VisitedUrls visited). Не бросает исключений наружу: ошибки -> счётчики.
 */
public class PagePipeline {
    // TODO: поля и методы см. Javadoc
}
