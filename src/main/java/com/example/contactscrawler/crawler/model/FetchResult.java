package com.example.contactscrawler.crawler.model;

import java.net.URI;
import java.time.Duration;

/**
 * Результат загрузки. httpStatus = 0 — ответа не было (таймаут/ошибка соединения).
 */
public record FetchResult(URI requestedUrl, URI finalUrl, int httpStatus, FetchStatus status,
                          String contentType, String body, Duration elapsed, String error) {

    public static FetchResult ok(URI requested, URI finalUrl, int httpStatus, String contentType, String body,
                                 Duration elapsed) {
        return new FetchResult(requested, finalUrl, httpStatus, FetchStatus.OK, contentType, body, elapsed, null);
    }

    public static FetchResult failure(URI requested, URI finalUrl, int httpStatus, FetchStatus status,
                                      String contentType, Duration elapsed, String error) {
        return new FetchResult(requested, finalUrl, httpStatus, status, contentType, null, elapsed, error);
    }

    public static FetchResult skippedByRobots(URI url) {
        return new FetchResult(url, url, 0, FetchStatus.SKIPPED_ROBOTS, null, null, Duration.ZERO, "robots.txt");
    }

    public boolean isOk() {
        return status == FetchStatus.OK;
    }
}
