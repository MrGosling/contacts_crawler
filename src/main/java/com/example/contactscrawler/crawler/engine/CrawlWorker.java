package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.VisitedUrls;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Воркер движка threads. {@code pending} = задачи в очереди + в обработке: инкремент ДО постановки в очередь,
 * декремент в finally после обработки. pending == 0 при пустой очереди ⇒ задание завершено.
 */
public class CrawlWorker implements Runnable {

    /** Общий volatile-флаг остановки для всех воркеров задания. */
    public static final class StopFlag {
        private volatile boolean running = true;

        public boolean isRunning() {
            return running;
        }

        public void stop() {
            running = false;
        }
    }

    private static final long IDLE_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

    private final ConcurrentLinkedQueue<CrawlTask> queue;
    private final PagePipeline pipeline;
    private final CrawlJob job;
    private final VisitedUrls visited;
    private final AtomicInteger pending;
    private final StopFlag flag;

    public CrawlWorker(ConcurrentLinkedQueue<CrawlTask> queue, PagePipeline pipeline, CrawlJob job,
                       VisitedUrls visited, AtomicInteger pending, StopFlag flag) {
        this.queue = queue;
        this.pipeline = pipeline;
        this.job = job;
        this.visited = visited;
        this.pending = pending;
        this.flag = flag;
    }

    @Override
    public void run() {
        while (flag.isRunning() && !Thread.currentThread().isInterrupted() && !job.isCancelled()) {
            CrawlTask task = queue.poll();
            if (task == null) {
                if (pending.get() == 0) {
                    flag.stop(); // очередь пуста и активных задач нет
                    return;
                }
                LockSupport.parkNanos(IDLE_PARK_NANOS);
                continue;
            }
            try {
                List<CrawlTask> children = pipeline.process(task, job, visited);
                for (CrawlTask child : children) {
                    pending.incrementAndGet();
                    queue.add(child);
                }
            } finally {
                pending.decrementAndGet();
            }
        }
    }
}
