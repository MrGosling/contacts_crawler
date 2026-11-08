package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.util.concurrent.TimeUnit;

class FeignFetcherTest extends AbstractFetcherTest {
    @Override
    protected PageFetcher create(CrawlerProperties props) {
        RawPageClient client = Feign.builder()
                .contract(new SpringMvcContract())
                .options(new Request.Options(props.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                        props.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS, false))
                .retryer(Retryer.NEVER_RETRY)
                .requestInterceptor(t -> t.header("User-Agent", props.getUserAgent()))
                .target(RawPageClient.class, "http://placeholder.invalid");
        return new FeignFetcher(client, props);
    }
}
