package com.example.contactscrawler.crawler;

/**
 * Повторы загрузки: до crawler.retries раз, backoff 1s, 2s; только таймауты/ошибки соединения/429/5xx (D-10).
 * Методы: FetchResult execute(Supplier<FetchResult> call) throws InterruptedException, boolean isRetryable(FetchResult).
 */
public class RetryPolicy {
    // TODO: поля и методы см. Javadoc
}
