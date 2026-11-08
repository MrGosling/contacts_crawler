package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.model.CrawlTask;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentStructuresTest {

    private static final int THREADS = 16;

    private static void runConcurrently(int threads, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                action.run();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    @RepeatedTest(10)
    void visitedUrlsMarksEachUrlExactlyOnce() throws Exception {
        VisitedUrls visited = new VisitedUrls();
        AtomicInteger firstTimes = new AtomicInteger();
        runConcurrently(THREADS, () -> {
            for (int i = 0; i < 1000; i++) {
                if (visited.markIfNew(URI.create("https://a.ru/p" + i))) {
                    firstTimes.incrementAndGet();
                }
            }
        });
        assertThat(firstTimes.get()).isEqualTo(1000);
        assertThat(visited.size()).isEqualTo(1000);
    }

    @RepeatedTest(10)
    void domainSlotsNeverExceedLimit() throws Exception {
        VisitedUrls visited = new VisitedUrls();
        AtomicInteger granted = new AtomicInteger();
        runConcurrently(THREADS, () -> {
            for (int i = 0; i < 100; i++) {
                if (visited.tryReserveDomainSlot("a.ru", 50)) {
                    granted.incrementAndGet();
                }
            }
        });
        assertThat(granted.get()).isEqualTo(50);
        assertThat(visited.pagesFor("a.ru")).isEqualTo(50);
        assertThat(visited.pagesFor("b.ru")).isZero();
        visited.clear();
        assertThat(visited.size()).isZero();
    }

    @Test
    void crawlQueueOrdersByPriorityThenDepthThenFifo() throws Exception {
        CrawlQueue q = new CrawlQueue(100);
        CrawlTask lowDeep = CrawlTask.of(URI.create("https://a.ru/1"), 2, 0, "j");
        CrawlTask lowShallowFirst = CrawlTask.of(URI.create("https://a.ru/2"), 1, 0, "j");
        CrawlTask lowShallowSecond = CrawlTask.of(URI.create("https://a.ru/3"), 1, 0, "j");
        CrawlTask high = CrawlTask.of(URI.create("https://a.ru/contacts"), 2, 10, "j");
        q.offer(lowDeep);
        q.offer(lowShallowSecond);
        q.offer(high);
        q.offer(lowShallowFirst);
        List<CrawlTask> out = new ArrayList<>();
        CrawlTask t;
        while ((t = q.poll(Duration.ofMillis(10))) != null) {
            out.add(t);
        }
        assertThat(out).containsExactly(high, lowShallowFirst, lowShallowSecond, lowDeep);
    }

    @Test
    void crawlQueueDropsOnOverflowAndClears() throws Exception {
        CrawlQueue q = new CrawlQueue(2);
        assertThat(q.offer(CrawlTask.of(URI.create("https://a.ru/1"), 0, 0, "j"))).isTrue();
        assertThat(q.offer(CrawlTask.of(URI.create("https://a.ru/2"), 0, 0, "j"))).isTrue();
        assertThat(q.offer(CrawlTask.of(URI.create("https://a.ru/3"), 0, 0, "j"))).isFalse();
        assertThat(q.droppedCount()).isEqualTo(1);
        assertThat(q.size()).isEqualTo(2);
        assertThat(q.clear()).isEqualTo(2);
        assertThat(q.offer(CrawlTask.of(URI.create("https://a.ru/4"), 0, 0, "j"))).isTrue();
    }

    @RepeatedTest(10)
    void crawlQueueProducersConsumersLoseNothing() throws Exception {
        CrawlQueue q = new CrawlQueue(100_000);
        Set<String> consumed = ConcurrentHashMap.newKeySet();
        AtomicInteger produced = new AtomicInteger();
        int producers = 8;
        int perProducer = 1000;
        CountDownLatch producersDone = new CountDownLatch(producers);
        ExecutorService pool = Executors.newFixedThreadPool(producers + 4);
        for (int p = 0; p < producers; p++) {
            int id = p;
            pool.submit(() -> {
                for (int i = 0; i < perProducer; i++) {
                    q.offer(CrawlTask.of(URI.create("https://a.ru/" + id + "/" + i), i % 3, i % 2, "j"));
                    produced.incrementAndGet();
                }
                producersDone.countDown();
            });
        }
        List<Future<?>> consumers = new ArrayList<>();
        for (int c = 0; c < 4; c++) {
            consumers.add(pool.submit(() -> {
                while (true) {
                    CrawlTask t = q.poll(Duration.ofMillis(50));
                    if (t != null) {
                        consumed.add(t.url().toString());
                    } else if (producersDone.getCount() == 0 && q.size() == 0) {
                        return null;
                    }
                }
            }));
        }
        for (Future<?> f : consumers) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(produced.get()).isEqualTo(producers * perProducer);
        assertThat(consumed).hasSize(producers * perProducer);
    }

    @RepeatedTest(10)
    void hostRateLimiterNeverExceedsPerHostConcurrency() throws Exception {
        HostRateLimiter limiter = new HostRateLimiter(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        runConcurrently(8, () -> {
            for (int i = 0; i < 20; i++) {
                try (HostRateLimiter.Permit p = limiter.acquire("a.ru", Duration.ZERO)) {
                    int now = active.incrementAndGet();
                    maxActive.accumulateAndGet(now, Math::max);
                    Thread.onSpinWait();
                    active.decrementAndGet();
                    done.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        assertThat(maxActive.get()).isEqualTo(1);
        assertThat(done.get()).isEqualTo(160);
        assertThat(limiter.availablePermits("a.ru")).isEqualTo(1);
    }

    @Test
    void hostRateLimiterKeepsDelayBetweenRequests() throws Exception {
        HostRateLimiter limiter = new HostRateLimiter(1);
        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) {
            limiter.acquire("a.ru", Duration.ofMillis(50)).close();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(150);
        // другой хост не ждёт
        long s2 = System.nanoTime();
        limiter.acquire("b.ru", Duration.ofMillis(500)).close();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s2)).isLessThan(400);
        assertThat(limiter.availablePermits("unknown")).isEqualTo(1);
    }

    @Test
    void permitCloseIsIdempotentAndInterruptReleases() throws Exception {
        HostRateLimiter limiter = new HostRateLimiter(1);
        HostRateLimiter.Permit p = limiter.acquire("a.ru", Duration.ofSeconds(30));
        p.close();
        p.close();
        assertThat(limiter.availablePermits("a.ru")).isEqualTo(1);
        Thread.currentThread().interrupt();
        try {
            limiter.acquire("a.ru", Duration.ofSeconds(30));
        } catch (InterruptedException expected) {
            // ожидаемо
        }
        assertThat(Thread.interrupted()).isFalse();
        assertThat(limiter.availablePermits("a.ru")).isEqualTo(1);
    }
}
