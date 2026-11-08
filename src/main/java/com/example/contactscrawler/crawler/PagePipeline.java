package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.crawler.model.CrawlTask;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.extract.ContactExtractionService;
import com.example.contactscrawler.extract.ExtractionResult;
import com.example.contactscrawler.logging.AsyncLogger;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.storage.ContactRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработка одной задачи, общая для всех движков: robots → rate limit → fetch (с повторами) → Jsoup.parse →
 * ContactExtractionService → ContactRepository.saveAll → LinkExtractor/UrlNormalizer/UrlFilter → новые CrawlTask.
 * Не бросает исключений наружу: ошибки → счётчики задания и лог. Сетевой I/O выполняется без удержания блокировок
 * (кроме Semaphore хоста).
 */
@Component
public class PagePipeline {

    private static final Logger log = LoggerFactory.getLogger(PagePipeline.class);

    private final RobotsTxtChecker robots;
    private final HostRateLimiter rateLimiter;
    private final PageFetcher fetcher;
    private final RetryPolicy retryPolicy;
    private final LinkExtractor linkExtractor;
    private final UrlNormalizer normalizer;
    private final UrlFilter filter;
    private final ContactExtractionService extraction;
    private final ContactRepository repository;
    private final AsyncLogger asyncLogger;
    private final PageRecorder pageRecorder;

    @Autowired
    public PagePipeline(RobotsTxtChecker robots, HostRateLimiter rateLimiter, PageFetcher fetcher,
                        RetryPolicy retryPolicy, LinkExtractor linkExtractor, UrlNormalizer normalizer,
                        UrlFilter filter, ContactExtractionService extraction, ContactRepository repository,
                        AsyncLogger asyncLogger, ObjectProvider<PageRecorder> pageRecorder) {
        this(robots, rateLimiter, fetcher, retryPolicy, linkExtractor, normalizer, filter, extraction, repository,
                asyncLogger, pageRecorder.getIfAvailable(() -> PageRecorder.NOOP));
    }

    public PagePipeline(RobotsTxtChecker robots, HostRateLimiter rateLimiter, PageFetcher fetcher,
                        RetryPolicy retryPolicy, LinkExtractor linkExtractor, UrlNormalizer normalizer,
                        UrlFilter filter, ContactExtractionService extraction, ContactRepository repository,
                        AsyncLogger asyncLogger, PageRecorder pageRecorder) {
        this.robots = robots;
        this.rateLimiter = rateLimiter;
        this.fetcher = fetcher;
        this.retryPolicy = retryPolicy;
        this.linkExtractor = linkExtractor;
        this.normalizer = normalizer;
        this.filter = filter;
        this.extraction = extraction;
        this.repository = repository;
        this.asyncLogger = asyncLogger;
        this.pageRecorder = pageRecorder;
    }

    /** Стартовые задачи задания (глубина 0); seed помечается посещённым. */
    public List<CrawlTask> seedTasks(CrawlJob job, VisitedUrls visited) {
        List<CrawlTask> out = new ArrayList<>();
        for (URI seed : job.params().seeds()) {
            normalizer.normalize(seed).flatMap(url -> admit(url, 0, job, visited)).ifPresent(out::add);
        }
        return out;
    }

    /** Полная обработка задачи; возвращает новые задачи для очереди. */
    public List<CrawlTask> process(CrawlTask task, CrawlJob job, VisitedUrls visited) {
        FetchResult result = fetch(task, job);
        if (result == null || !result.isOk()) {
            return List.of();
        }
        try {
            Document doc = Jsoup.parse(result.body(), result.finalUrl().toString());
            store(extraction.extract(doc, result.finalUrl()), result.finalUrl(), job);
            return discover(doc, result.finalUrl(), task, job, visited);
        } catch (RuntimeException e) {
            log.warn("Processing of {} failed: {}", task.url(), e.toString());
            return List.of();
        }
    }

    /**
     * Этап загрузки: robots → rate limit → fetch с повторами → проверка конечного URL после редиректа → счётчики/лог.
     *
     * @return результат или null, если поток прерван
     */
    public FetchResult fetch(CrawlTask task, CrawlJob job) {
        URI url = task.url();
        FetchResult result;
        try {
            if (!robots.isAllowed(url)) {
                result = FetchResult.skippedByRobots(url);
            } else {
                Duration delay = Duration.ofMillis(job.params().delayMs());
                Optional<Duration> crawlDelay = robots.crawlDelay(url);
                if (crawlDelay.isPresent() && crawlDelay.get().compareTo(delay) > 0) {
                    delay = crawlDelay.get();
                }
                try (HostRateLimiter.Permit ignored = rateLimiter.acquire(url.getHost(), delay)) {
                    result = retryPolicy.execute(() -> fetcher.fetch(url));
                }
                if (result.isOk() && !result.finalUrl().equals(url)) {
                    URI fin = normalizer.normalize(result.finalUrl()).orElse(result.finalUrl());
                    if (!filter.domainAllowed(fin, job.params()) || !robots.isAllowed(fin)) {
                        result = FetchResult.skippedByRobots(url);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            log.warn("Fetch of {} failed: {}", url, e.toString());
            job.pagesFailed.increment();
            return null;
        }
        switch (result.status()) {
            case OK -> job.pagesFetched.increment();
            case FAILED -> job.pagesFailed.increment();
            default -> job.pagesSkipped.increment();
        }
        asyncLogger.info(String.format("job=%s url=%s status=%s http=%d time=%dms%s", job.id(), url,
                result.status(), result.httpStatus(), result.elapsed().toMillis(),
                result.error() == null ? "" : " error=" + result.error()));
        try {
            pageRecorder.record(job, task, result);
        } catch (RuntimeException e) {
            log.debug("Page record failed: {}", e.toString());
        }
        return result;
    }

    /** Сохранение контактов и названия организации. */
    public void store(ExtractionResult result, URI pageUrl, CrawlJob job) {
        List<Contact> contacts = result.contacts().stream().map(c -> c.withJobId(job.id())).toList();
        if (!contacts.isEmpty()) {
            repository.saveAll(contacts);
            job.contactsFound.add(contacts.size());
        }
        result.organizationName().ifPresent(name -> repository.saveOrganization(
                UrlNormalizer.domainOf(pageUrl), name, ContactExtractionService.websiteOf(pageUrl)));
    }

    /** Новые задачи из ссылок загруженной страницы. */
    public List<CrawlTask> discover(FetchResult result, CrawlTask task, CrawlJob job, VisitedUrls visited) {
        if (result == null || !result.isOk() || task.depth() >= job.params().maxDepth()) {
            return List.of();
        }
        return discover(Jsoup.parse(result.body(), result.finalUrl().toString()), result.finalUrl(), task, job, visited);
    }

    private List<CrawlTask> discover(Document doc, URI base, CrawlTask task, CrawlJob job, VisitedUrls visited) {
        int depth = task.depth() + 1;
        if (depth > job.params().maxDepth()) {
            return List.of();
        }
        List<CrawlTask> out = new ArrayList<>();
        for (String href : linkExtractor.extract(doc, base)) {
            normalizer.normalize(href, base).flatMap(url -> admit(url, depth, job, visited)).ifPresent(out::add);
        }
        return out;
    }

    private Optional<CrawlTask> admit(URI url, int depth, CrawlJob job, VisitedUrls visited) {
        CrawlParams params = job.params();
        if (!filter.accept(url, depth, params) || !visited.markIfNew(url)) {
            return Optional.empty();
        }
        if (!visited.tryReserveDomainSlot(UrlNormalizer.domainOf(url), params.maxPagesPerDomain())) {
            return Optional.empty();
        }
        return Optional.of(CrawlTask.of(url, depth, filter.priorityOf(url), job.id()));
    }
}
