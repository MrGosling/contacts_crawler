package com.example.contactscrawler.crawler;

/**
 * Обёртка над PriorityBlockingQueue<CrawlTask> с ограничением ёмкости (crawler.queue-capacity) и счётчиком отброшенных.
 * Методы: boolean offer(CrawlTask) (false при переполнении), CrawlTask poll(Duration timeout) throws InterruptedException,
 * int size(), void clear(), long droppedCount().
 */
public class CrawlQueue {
    // TODO: поля и методы см. Javadoc
}
