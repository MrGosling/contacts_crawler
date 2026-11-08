package com.example.contactscrawler.crawler.engine;

/**
 * Этап 4. implements CrawlEngine. Обход «волнами» по глубине: задачи уровня загружаются через CompletableFuture.supplyAsync(..., forkJoinPool),
 * извлечение контактов — ParallelExtractionService (RecursiveTask). Кастомный ForkJoinPool, commonPool не используется.
 */
public class ForkJoinCrawlEngine {
    // TODO: поля и методы см. Javadoc
}
