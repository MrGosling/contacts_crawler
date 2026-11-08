package com.example.contactscrawler.crawler.engine;

/**
 * Параллельное извлечение контактов из набора страниц в кастомном ForkJoinPool.
 * Методы: List<Contact> extractAll(List<FetchResult> pages). Внутренний ExtractTask extends RecursiveTask<List<Contact>>
 * (порог разбиения THRESHOLD страниц). Результат должен совпадать с последовательной обработкой (тест).
 */
public class ParallelExtractionService {
    // TODO: поля и методы см. Javadoc
}
