package com.example.contactscrawler.crawler.scheduler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.CrawlerService;
import com.example.contactscrawler.crawler.model.CrawlParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Вариант на @Scheduled (crawler.scheduler.mode=spring). Защита от наложения: ReentrantLock.tryLock()
 * + пропуск, если задание выполняется (CrawlerService.trySubmitScheduled).
 */
@Component
@ConditionalOnProperty(name = "crawler.scheduler.mode", havingValue = "spring")
public class SpringCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpringCrawlScheduler.class);

    private final CrawlerService crawlerService;
    private final SeedService seedService;
    private final CrawlerProperties props;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong runs = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();

    public SpringCrawlScheduler(CrawlerService crawlerService, SeedService seedService, CrawlerProperties props) {
        this.crawlerService = crawlerService;
        this.seedService = seedService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${crawler.scheduler.fixed-delay}",
            initialDelayString = "${crawler.scheduler.initial-delay}")
    public void tick() {
        if (!lock.tryLock()) {
            skipped.incrementAndGet();
            log.info("Scheduled tick skipped: previous tick still in progress");
            return;
        }
        try {
            List<URI> seeds = seedService.all();
            if (!seeds.isEmpty() && crawlerService.trySubmitScheduled(CrawlParams.defaults(seeds, props))) {
                runs.incrementAndGet();
            } else {
                skipped.incrementAndGet();
            }
        } catch (RuntimeException e) {
            log.warn("Scheduled tick failed: {}", e.toString());
        } finally {
            lock.unlock();
        }
    }

    public long runs() {
        return runs.get();
    }

    public long skipped() {
        return skipped.get();
    }
}
