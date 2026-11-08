package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import feign.Request;
import feign.RequestInterceptor;
import feign.Response;
import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Feign-клиент для произвольных URL (Q-9): параметр типа URI переопределяет базовый URL.
 * Редиректы выключены (их выполняет AbstractPageFetcher), повторы Feign выключены (RetryPolicy).
 */
@FeignClient(name = "raw-page", url = "http://placeholder.invalid", configuration = RawPageClient.Config.class)
public interface RawPageClient {

    @GetMapping
    Response get(URI url);

    /** Конфигурация только этого клиента (не @Configuration, чтобы не влиять на другие клиенты). */
    class Config {
        @Bean
        Request.Options rawPageOptions(CrawlerProperties props) {
            return new Request.Options(props.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                    props.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS, false);
        }

        @Bean
        Retryer rawPageRetryer() {
            return Retryer.NEVER_RETRY;
        }

        @Bean
        RequestInterceptor rawPageUserAgent(CrawlerProperties props) {
            return template -> template.header(HttpHeaders.USER_AGENT, props.getUserAgent());
        }
    }
}
