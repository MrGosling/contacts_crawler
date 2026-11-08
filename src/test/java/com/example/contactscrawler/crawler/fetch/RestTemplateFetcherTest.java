package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.config.HttpClientConfig;

class RestTemplateFetcherTest extends AbstractFetcherTest {
    @Override
    protected PageFetcher create(CrawlerProperties props) {
        return new RestTemplateFetcher(HttpClientConfig.newRestTemplate(props), props);
    }
}
