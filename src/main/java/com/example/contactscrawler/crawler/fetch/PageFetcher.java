package com.example.contactscrawler.crawler.fetch;

/**
 * Загрузка страницы. Методы: FetchResult fetch(URI url). Общие требования ко всем реализациям: таймауты connect 5s / read 10s,
 * до 3 редиректов, только text/html|application/xhtml+xml, лимит crawler.max-page-size, User-Agent.
 */
public interface PageFetcher {
    // TODO: методы см. Javadoc
}
