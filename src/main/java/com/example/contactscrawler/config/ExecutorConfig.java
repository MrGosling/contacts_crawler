package com.example.contactscrawler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Бины пулов: ThreadPoolExecutor для ExecutorCrawlEngine (CrawlerThreadFactory, ArrayBlockingQueue ёмкостью
 * crawler.executor.queue-capacity, LoggingCallerRunsPolicy), ForkJoinPool(crawler.forkjoin.parallelism),
 * ScheduledExecutorService для CrawlScheduler (1 поток, демон). destroyMethod — graceful shutdown.
 */
@Configuration
@EnableConfigurationProperties(CrawlerProperties.class)
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);

    @Bean(destroyMethod = "gracefulShutdown")
    public GracefulThreadPoolExecutor crawlerExecutor(CrawlerProperties props) {
        return new GracefulThreadPoolExecutor(props.getThreads(),
                new ArrayBlockingQueue<>(props.getExecutor().getQueueCapacity()),
                new CrawlerThreadFactory("crawler-worker", false),
                new LoggingCallerRunsPolicy(),
                props.getExecutor().getShutdownTimeout());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ForkJoinPool crawlerForkJoinPool(CrawlerProperties props) {
        return newForkJoinPool(props.getForkjoin().getParallelism());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService crawlSchedulerExecutor() {
        return Executors.newSingleThreadScheduledExecutor(new CrawlerThreadFactory("crawl-scheduler", true));
    }

    /** Кастомный ForkJoinPool (commonPool не используется), потоки {@code crawler-fj-N}. */
    public static ForkJoinPool newForkJoinPool(int parallelism) {
        return new ForkJoinPool(parallelism, pool -> {
            ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setName("crawler-fj-" + t.getPoolIndex());
            return t;
        }, (t, e) -> log.error("Uncaught exception in {}", t.getName(), e), false);
    }

    /**
     * shutdown → awaitTermination(timeout) → shutdownNow (→ ещё одно короткое ожидание).
     *
     * @return true, если пул завершился без shutdownNow
     */
    public static boolean shutdownGracefully(ExecutorService executor, Duration timeout, String name) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.info("Executor {} terminated gracefully", name);
                return true;
            }
            log.warn("Executor {} did not terminate in {}, forcing shutdownNow", name, timeout);
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.error("Executor {} did not terminate after shutdownNow", name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /** ThreadPoolExecutor с graceful shutdown как destroy-методом Spring. */
    public static class GracefulThreadPoolExecutor extends ThreadPoolExecutor {
        private final Duration shutdownTimeout;

        public GracefulThreadPoolExecutor(int threads, BlockingQueue<Runnable> queue, ThreadFactory threadFactory,
                                          RejectedExecutionHandler handler, Duration shutdownTimeout) {
            super(threads, threads, 60, TimeUnit.SECONDS, queue, threadFactory, handler);
            this.shutdownTimeout = shutdownTimeout;
        }

        public boolean gracefulShutdown() {
            return shutdownGracefully(this, shutdownTimeout, "crawler-worker pool");
        }
    }
}
