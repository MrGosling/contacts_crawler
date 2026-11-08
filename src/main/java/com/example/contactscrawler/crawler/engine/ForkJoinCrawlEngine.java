package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.crawler.PagePipeline;
import com.example.contactscrawler.crawler.VisitedUrls;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.extract.ExtractionResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Этап 4 (crawler.engine=forkjoin). Обход «волнами» по глубине: страницы уровня загружаются через
 * CompletableFuture.supplyAsync(..., forkJoinPool), извлечение — ParallelExtractionService (RecursiveTask).
 * Кастомный ForkJoinPool, commonPool не используется.
 */
@Component
@ConditionalOnProperty(name = "crawler.engine", havingValue = "forkjoin")
public class ForkJoinCrawlEngine implements CrawlEngine {

    private final PagePipeline pipeline;
    private final ParallelExtractionService extraction;
    private final ForkJoinPool pool;

    private volatile VisitedUrls visited = new VisitedUrls();
    private volatile boolean stopRequested;
    private final AtomicInteger levelRemaining = new AtomicInteger();

    public ForkJoinCrawlEngine(PagePipeline pipeline, ParallelExtractionService extraction,
                               ForkJoinPool crawlerForkJoinPool) {
        this.pipeline = pipeline;
        this.extraction = extraction;
        this.pool = crawlerForkJoinPool;
    }

    private record Fetched(CrawlTask task, FetchResult result) {
    }

    @Override
    public void run(CrawlJob job) {
        VisitedUrls v = new VisitedUrls();
        visited = v;
        stopRequested = false;
        List<CrawlTask> level = pipeline.seedTasks(job, v);
        while (!level.isEmpty() && !stopped(job)) {
            levelRemaining.set(level.size());
            List<CompletableFuture<Fetched>> futures = level.stream()
                    .map(t -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return stopped(job) ? null : new Fetched(t, pipeline.fetch(t, job));
                        } finally {
                            levelRemaining.decrementAndGet();
                        }
                    }, pool))
                    .toList();
            List<Fetched> ok = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(f -> f != null && f.result() != null && f.result().isOk())
                    .toList();
            if (stopped(job)) {
                break;
            }
            List<ExtractionResult> results = extraction.extractResults(ok.stream().map(Fetched::result).toList());
            for (int i = 0; i < ok.size(); i++) {
                pipeline.store(results.get(i), ok.get(i).result().finalUrl(), job);
            }
            List<CrawlTask> next = new ArrayList<>();
            for (Fetched f : ok) {
                next.addAll(pipeline.discover(f.result(), f.task(), job, v));
            }
            next.sort(null);
            level = next;
        }
        levelRemaining.set(0);
    }

    private boolean stopped(CrawlJob job) {
        return stopRequested || job.isCancelled() || Thread.currentThread().isInterrupted();
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public int activeThreads() {
        return pool.getActiveThreadCount();
    }

    @Override
    public int poolSize() {
        return pool.getParallelism();
    }

    @Override
    public int queueSize() {
        return Math.max(0, levelRemaining.get());
    }

    @Override
    public int visitedUrls() {
        return visited.size();
    }

    @Override
    public String name() {
        return "forkjoin";
    }
}
