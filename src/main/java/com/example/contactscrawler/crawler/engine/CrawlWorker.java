package com.example.contactscrawler.crawler.engine;

/**
 * implements Runnable. Поля: ConcurrentLinkedQueue<CrawlTask> queue, PagePipeline pipeline, CrawlJob job,
 * AtomicInteger activeTasks, volatile-флаг остановки (общий holder).
 * run(): пока !stopped && !interrupted — poll задачу, activeTasks++, pipeline.process, новые задачи в очередь, activeTasks-- в finally;
 * при пустой очереди — короткое ожидание через LockSupport.parkNanos. InterruptedException -> восстановить флаг и выйти.
 */
public class CrawlWorker {
    // TODO: поля и методы см. Javadoc
}
