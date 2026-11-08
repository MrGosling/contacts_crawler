package com.example.contactscrawler.crawler.model;

/**
 * Результат загрузки страницы.
 */
public enum FetchStatus {
    OK, FAILED, SKIPPED_ROBOTS, TOO_LARGE, NOT_HTML
}
