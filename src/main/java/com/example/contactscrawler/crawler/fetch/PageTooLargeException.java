package com.example.contactscrawler.crawler.fetch;

/**
 * Тело ответа превышает crawler.max-page-size (→ TOO_LARGE).
 */
public class PageTooLargeException extends RuntimeException {
    public PageTooLargeException(long limit) {
        super("Response body exceeds " + limit + " bytes");
    }
}
