package com.example.contactscrawler.crawler.scheduler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.config.ExecutorConfig;
import com.example.contactscrawler.crawler.CrawlerService;
import com.example.contactscrawler.crawler.model.CrawlParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Планировщик на ScheduledExecutorService (crawler.scheduler.mode=executor): scheduleWithFixedDelay(tick).
 * Защита от наложения: AtomicBoolean.compareAndSet для самого тика + пропуск, если задание уже выполняется
 * или ожидает (CrawlerService.trySubmitScheduled).
 */
@Component
@ConditionalOnProperty(name = "crawler.scheduler.mode", havingValue = "executor", matchIfMissing = true)
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final CrawlerService crawlerService;
    private final SeedService seedService;
    private final ScheduledExecutorService executor;
    private final CrawlerProperties props;
    private final AtomicBoolean inProgress = new AtomicBoolean();
    private final AtomicLong runs = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private volatile ScheduledFuture<?> handle;

    public CrawlScheduler(CrawlerService crawlerService, SeedService seedService,
                          @Qualifier("crawlSchedulerExecutor") ScheduledExecutorService executor,
                          CrawlerProperties props) {
        this.crawlerService = crawlerService;
        this.seedService = seedService;
        this.executor = executor;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        Duration initial = props.getScheduler().getInitialDelay();
        Duration delay = props.getScheduler().getFixedDelay();
        handle = executor.scheduleWithFixedDelay(this::tick, initial.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        log.info("CrawlScheduler started: initialDelay={}, fixedDelay={}", initial, delay);
    }

    /** Один плановый запуск. Исключения не выбрасываются (иначе ScheduledExecutorService отменит расписание). */
    public void tick() {
        if (!inProgress.compareAndSet(false, true)) {
            skipped.incrementAndGet();
            log.info("Scheduled tick skipped: previous tick still in progress");
            return;
        }
        try {
            List<URI> seeds = seedService.all();
            if (seeds.isEmpty()) {
                skipped.incrementAndGet();
                log.info("Scheduled tick skipped: no seed URLs");
                return;
            }
            if (crawlerService.trySubmitScheduled(CrawlParams.defaults(seeds, props))) {
                runs.incrementAndGet();
            } else {
                skipped.incrementAndGet();
            }
        } catch (RuntimeException e) {
            log.warn("Scheduled tick failed: {}", e.toString());
        } finally {
            inProgress.set(false);
        }
    }

    @PreDestroy
    public void stop() {
        ScheduledFuture<?> h = handle;
        if (h != null) {
            h.cancel(false);
        }
        ExecutorConfig.shutdownGracefully(executor, Duration.ofSeconds(5), "crawl-scheduler");
    }

    public long runs() {
        return runs.get();
    }

    public long skipped() {
        return skipped.get();
    }
}
