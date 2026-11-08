package com.example.contactscrawler.config;

import com.example.contactscrawler.crawler.fetch.RawPageClient;
import com.example.contactscrawler.integration.TwoGisClient;
import io.netty.channel.ChannelOption;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * HTTP-клиенты. Редиректы клиенты сами НЕ выполняют: их обрабатывает {@code AbstractPageFetcher}
 * (до crawler.max-redirects, единообразно для всех реализаций).
 */
@Configuration
@EnableFeignClients(clients = {RawPageClient.class, TwoGisClient.class})
public class HttpClientConfig {

    @Bean
    public WebClient crawlerWebClient(CrawlerProperties props) {
        return newWebClient(props);
    }

    @Bean
    public RestTemplate crawlerRestTemplate(CrawlerProperties props) {
        return newRestTemplate(props);
    }

    public static WebClient newWebClient(CrawlerProperties props) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) props.getConnectTimeout().toMillis())
                .responseTimeout(props.getReadTimeout())
                .followRedirect(false);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.USER_AGENT, props.getUserAgent())
                .codecs(c -> c.defaultCodecs().maxInMemorySize((int) props.getMaxPageSize().toBytes()))
                .build();
    }

    public static RestTemplate newRestTemplate(CrawlerProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(props.getConnectTimeout());
        factory.setReadTimeout(props.getReadTimeout());
        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false; // статусы обрабатывает фетчер
            }
        });
        return rt;
    }
}
