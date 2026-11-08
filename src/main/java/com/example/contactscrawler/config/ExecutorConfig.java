package com.example.contactscrawler.config;

/**
 * @Configuration. Бины пулов: ThreadPoolExecutor для ExecutorCrawlEngine (CrawlerThreadFactory, ArrayBlockingQueue
 * ёмкостью crawler.executor.queue-capacity, LoggingCallerRunsPolicy), ForkJoinPool(crawler.forkjoin.parallelism),
 * ScheduledExecutorService для CrawlScheduler (1 поток, демон). destroyMethod — graceful shutdown.
 */
public class ExecutorConfig {
    // TODO: поля и методы см. Javadoc
}
