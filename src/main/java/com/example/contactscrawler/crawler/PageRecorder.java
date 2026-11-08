package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlTask;
import com.example.contactscrawler.crawler.model.FetchResult;

/**
 * Учёт загруженных страниц (таблица page при crawler.storage=h2). По умолчанию — no-op.
 */
@FunctionalInterface
public interface PageRecorder {

    PageRecorder NOOP = (job, task, result) -> { };

    void record(CrawlJob job, CrawlTask task, FetchResult result);
}
