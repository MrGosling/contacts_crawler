package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.crawler.PageRecorder;
import com.example.contactscrawler.crawler.UrlNormalizer;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;
import com.example.contactscrawler.crawler.model.FetchResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Запись страниц в таблицу page (crawler.storage=h2). Отдельная короткая транзакция на запись (save репозитория).
 */
@Component
@ConditionalOnProperty(name = "crawler.storage", havingValue = "h2", matchIfMissing = true)
public class JpaPageRecorder implements PageRecorder {

    private final PageJpaRepository pages;

    public JpaPageRecorder(PageJpaRepository pages) {
        this.pages = pages;
    }

    @Override
    public void record(CrawlJob job, CrawlTask task, FetchResult result) {
        pages.save(new PageEntity(job.id(), task.url().toString(), UrlNormalizer.domainOf(task.url()), task.depth(),
                result.httpStatus(), result.status(), Instant.now()));
    }
}
