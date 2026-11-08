package com.example.contactscrawler.crawler.engine;

/**
 * Этап 2. implements CrawlEngine. ThreadPoolExecutor из ExecutorConfig, CrawlQueue (PriorityBlockingQueue),
 * AtomicInteger/Phaser activeTasks (инкремент ДО постановки, декремент в finally), CountDownLatch завершения.
 * Диспетчер берёт задачи из CrawlQueue.poll(timeout) и отправляет в пул. stop(): очистка очереди, отмена Future.
 */
public class ExecutorCrawlEngine {
    // TODO: поля и методы см. Javadoc
}
