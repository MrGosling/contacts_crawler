package com.example.contactscrawler.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ExecutorConfigTest {

    @Test
    void threadNamesAndUncaughtHandler() throws Exception {
        CrawlerThreadFactory factory = new CrawlerThreadFactory();
        Thread t1 = factory.newThread(() -> { });
        Thread t2 = factory.newThread(() -> {
            throw new IllegalStateException("boom");
        });
        assertThat(t1.getName()).isEqualTo("crawler-worker-1");
        assertThat(t2.getName()).isEqualTo("crawler-worker-2");
        assertThat(t1.isDaemon()).isFalse();
        t2.start();
        t2.join();
        assertThat(factory.uncaughtCount()).isEqualTo(1);
    }

    @Test
    void poolThreadsAreNamedAndRejectedTasksRunInCaller() throws Exception {
        CrawlerProperties props = new CrawlerProperties();
        props.setThreads(1);
        props.getExecutor().setQueueCapacity(1);
        ExecutorConfig.GracefulThreadPoolExecutor pool = new ExecutorConfig().crawlerExecutor(props);
        LoggingCallerRunsPolicy policy = (LoggingCallerRunsPolicy) pool.getRejectedExecutionHandler();
        CountDownLatch block = new CountDownLatch(1);
        AtomicReference<String> workerName = new AtomicReference<>();
        pool.execute(() -> {
            workerName.set(Thread.currentThread().getName());
            awaitLatch(block);
        });
        pool.execute(() -> awaitLatch(block)); // в очередь
        AtomicReference<String> rejectedRanIn = new AtomicReference<>();
        pool.execute(() -> rejectedRanIn.set(Thread.currentThread().getName())); // переполнение → CallerRuns
        assertThat(rejectedRanIn.get()).isEqualTo(Thread.currentThread().getName());
        assertThat(policy.rejectedCount()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> workerName.get() != null);
        assertThat(workerName.get()).startsWith("crawler-worker-");
        block.countDown();
        assertThat(pool.gracefulShutdown()).isTrue();
        assertThatThrownBy(() -> pool.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(policy.rejectedCount()).isEqualTo(2);
    }

    @Test
    void gracefulShutdownForcesStuckTasks() throws Exception {
        ExecutorConfig.GracefulThreadPoolExecutor pool = new ExecutorConfig.GracefulThreadPoolExecutor(1,
                new ArrayBlockingQueue<>(1), new CrawlerThreadFactory("t", false), new LoggingCallerRunsPolicy(),
                Duration.ofMillis(100));
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        pool.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.complete(true);
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pool.gracefulShutdown()).isFalse();
        assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pool.isTerminated()).isTrue();
    }

    @Test
    void customForkJoinPoolIsNotCommon() throws Exception {
        ForkJoinPool pool = new ExecutorConfig().crawlerForkJoinPool(new CrawlerProperties());
        assertThat(pool).isNotSameAs(ForkJoinPool.commonPool());
        assertThat(pool.getParallelism()).isEqualTo(4);
        assertThat(pool.submit(() -> Thread.currentThread().getName()).get()).startsWith("crawler-fj-");
        pool.shutdownNow();
        var scheduler = new ExecutorConfig().crawlSchedulerExecutor();
        assertThat(scheduler.schedule(() -> Thread.currentThread().isDaemon(), 1, TimeUnit.MILLISECONDS).get()).isTrue();
        scheduler.shutdownNow();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
