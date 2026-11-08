package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.crawler.model.CrawlJob;

/**
 * Движок обхода (crawler.engine). run блокирует вызывающий поток до завершения задания
 * (очередь пуста и нет активных задач) или до остановки/отмены. Статус задания выставляет CrawlerService.
 */
public interface CrawlEngine {

    void run(CrawlJob job);

    /** Прерывание текущего задания. */
    void stop();

    int activeThreads();

    int poolSize();

    int queueSize();

    int visitedUrls();

    String name();
}
