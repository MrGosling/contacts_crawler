package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.CrawlQueue;
import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.VisitedUrls;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Этап 2 (crawler.engine=executor, по умолчанию). ThreadPoolExecutor из ExecutorConfig, CrawlQueue
 * (PriorityBlockingQueue), AtomicInteger pending (инкремент ДО постановки, декремент в finally), CountDownLatch
 * завершения. Диспетчер (вызывающий поток) берёт задачи через CrawlQueue.poll(timeout) и отправляет в пул;
 * Semaphore(threads) ограничивает число задач в пуле, чтобы порядок приоритетов определяла CrawlQueue.
 */
@Component
@ConditionalOnProperty(name = "crawler.engine", havingValue = "executor", matchIfMissing = true)
public class ExecutorCrawlEngine implements CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutorCrawlEngine.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    private final PagePipeline pipeline;
    private final ThreadPoolExecutor pool;
    private final int queueCapacity;
    private final int maxInFlight;

    private volatile CrawlQueue queue;
    private volatile VisitedUrls visited = new VisitedUrls();
    private volatile boolean stopRequested;
    private final Set<FutureTask<?>> inFlight = ConcurrentHashMap.newKeySet();

    public ExecutorCrawlEngine(PagePipeline pipeline, ThreadPoolExecutor crawlerExecutor, CrawlerProperties props) {
        this.pipeline = pipeline;
        this.pool = crawlerExecutor;
        this.queueCapacity = props.getQueueCapacity();
        this.maxInFlight = props.getThreads();
        this.queue = new CrawlQueue(queueCapacity);
    }

    @Override
    public void run(CrawlJob job) {
        CrawlQueue q = new CrawlQueue(queueCapacity);
        VisitedUrls v = new VisitedUrls();
        AtomicInteger pending = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        Semaphore slots = new Semaphore(maxInFlight);
        queue = q;
        visited = v;
        stopRequested = false;

        for (CrawlTask t : pipeline.seedTasks(job, v)) {
            pending.incrementAndGet();
            if (!q.offer(t)) {
                pending.decrementAndGet();
            }
        }
        if (pending.get() == 0) {
            return;
        }
        try {
            while (!stopRequested && !job.isCancelled() && done.getCount() > 0) {
                if (!slots.tryAcquire(POLL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    continue;
                }
                CrawlTask task = q.poll(POLL_TIMEOUT);
                if (task == null) {
                    slots.release();
                    continue;
                }
                TrackedTask ft = new TrackedTask(() -> {
                    try {
                        for (CrawlTask child : pipeline.process(task, job, v)) {
                            pending.incrementAndGet();
                            if (!q.offer(child)) {
                                pending.decrementAndGet(); // отброшено при переполнении
                            }
                        }
                    } finally {
                        slots.release();
                        if (pending.decrementAndGet() == 0) {
                            done.countDown();
                        }
                    }
                });
                inFlight.add(ft);
                try {
                    pool.execute(ft);
                } catch (RejectedExecutionException e) {
                    inFlight.remove(ft);
                    log.warn("Pool rejected task (shutting down?): {}", e.getMessage());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (done.getCount() > 0) {
                cancelInFlight(q);
            }
        }
    }

    private void cancelInFlight(CrawlQueue q) {
        int dropped = q.clear();
        inFlight.forEach(f -> f.cancel(true));
        inFlight.clear();
        log.info("Executor engine stopped, {} queued tasks discarded", dropped);
    }

    @Override
    public void stop() {
        stopRequested = true;
        queue.clear();
        inFlight.forEach(f -> f.cancel(true));
    }

    @Override
    public int activeThreads() {
        return pool.getActiveCount();
    }

    @Override
    public int poolSize() {
        return pool.getPoolSize();
    }

    @Override
    public int queueSize() {
        return queue.size();
    }

    @Override
    public int visitedUrls() {
        return visited.size();
    }

    public long droppedTasks() {
        return queue.droppedCount();
    }

    @Override
    public String name() {
        return "executor";
    }

    /** FutureTask, удаляющий себя из множества inFlight по завершении. */
    private final class TrackedTask extends FutureTask<Void> {
        TrackedTask(Runnable r) {
            super(r, null);
        }

        @Override
        protected void done() {
            inFlight.remove(this);
        }
    }
}
