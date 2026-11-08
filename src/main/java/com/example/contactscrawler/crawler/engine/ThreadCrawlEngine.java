package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.VisitedUrls;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Этап 1 (crawler.engine=threads). N потоков через new Thread(new CrawlWorker(...), "crawler-thread-N"),
 * общая ConcurrentLinkedQueue, VisitedUrls, AtomicInteger pending. stop(): volatile-флаг + interrupt() + join().
 */
@Component
@ConditionalOnProperty(name = "crawler.engine", havingValue = "threads")
public class ThreadCrawlEngine implements CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(ThreadCrawlEngine.class);

    private final PagePipeline pipeline;
    private final int threadCount;

    private volatile ConcurrentLinkedQueue<CrawlTask> queue = new ConcurrentLinkedQueue<>();
    private volatile VisitedUrls visited = new VisitedUrls();
    private volatile CrawlWorker.StopFlag flag = new CrawlWorker.StopFlag();
    private volatile List<Thread> threads = List.of();

    public ThreadCrawlEngine(PagePipeline pipeline, CrawlerProperties props) {
        this.pipeline = pipeline;
        this.threadCount = props.getThreads();
    }

    @Override
    public void run(CrawlJob job) {
        ConcurrentLinkedQueue<CrawlTask> q = new ConcurrentLinkedQueue<>();
        VisitedUrls v = new VisitedUrls();
        CrawlWorker.StopFlag f = new CrawlWorker.StopFlag();
        AtomicInteger pending = new AtomicInteger();
        queue = q;
        visited = v;
        flag = f;
        for (CrawlTask t : pipeline.seedTasks(job, v)) {
            pending.incrementAndGet();
            q.add(t);
        }
        if (pending.get() == 0 || job.isCancelled()) {
            return;
        }
        List<Thread> started = new ArrayList<>(threadCount);
        for (int i = 1; i <= threadCount; i++) {
            Thread t = new Thread(new CrawlWorker(q, pipeline, job, v, pending, f), "crawler-thread-" + i);
            t.setUncaughtExceptionHandler((th, e) -> log.error("Uncaught exception in {}", th.getName(), e));
            started.add(t);
        }
        threads = started;
        started.forEach(Thread::start);
        for (Thread t : started) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
                return;
            }
        }
    }

    @Override
    public void stop() {
        flag.stop();
        List<Thread> current = threads;
        current.forEach(Thread::interrupt);
        for (Thread t : current) {
            if (t == Thread.currentThread()) {
                continue;
            }
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        queue.clear();
    }

    @Override
    public int activeThreads() {
        return (int) threads.stream().filter(Thread::isAlive).count();
    }

    @Override
    public int poolSize() {
        return threadCount;
    }

    @Override
    public int queueSize() {
        return queue.size();
    }

    @Override
    public int visitedUrls() {
        return visited.size();
    }

    @Override
    public String name() {
        return "threads";
    }
}
