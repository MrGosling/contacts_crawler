package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.config.HttpClientConfig;

class WebClientFetcherTest extends AbstractFetcherTest {
    @Override
    protected PageFetcher create(CrawlerProperties props) {
        return new WebClientFetcher(HttpClientConfig.newWebClient(props), props);
    }
}
