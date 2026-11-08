package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.crawler.model.FetchResult;

import java.net.URI;

/**
 * Загрузка страницы. Общие требования ко всем реализациям: таймауты connect 5s / read 10s, до 3 редиректов,
 * только text/html|application/xhtml+xml, лимит crawler.max-page-size, User-Agent.
 */
public interface PageFetcher {

    /** HTML-страница (другие Content-Type → NOT_HTML). */
    FetchResult fetch(URI url);

    /** Произвольный текстовый ресурс (robots.txt), без проверки Content-Type. */
    FetchResult fetchText(URI url);
}
