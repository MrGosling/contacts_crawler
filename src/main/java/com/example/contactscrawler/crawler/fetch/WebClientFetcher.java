package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

/**
 * Основная реализация PageFetcher (crawler.fetcher=webclient) на WebClient с .block(timeout).
 * Лимит размера — maxInMemorySize кодеков (DataBufferLimitException → TOO_LARGE) + Content-Length.
 */
@Component
@ConditionalOnProperty(name = "crawler.fetcher", havingValue = "webclient", matchIfMissing = true)
public class WebClientFetcher extends AbstractPageFetcher {

    private final WebClient webClient;
    private final Duration blockTimeout;

    public WebClientFetcher(WebClient crawlerWebClient, CrawlerProperties props) {
        super(props);
        this.webClient = crawlerWebClient;
        this.blockTimeout = props.getConnectTimeout().plus(props.getReadTimeout()).plusSeconds(2);
    }

    @Override
    protected RawResponse execute(URI url, boolean htmlOnly) {
        return webClient.get().uri(url).exchangeToMono(resp -> {
            int status = resp.statusCode().value();
            String ct = resp.headers().contentType().map(MediaType::toString).orElse(null);
            String location = resp.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
            if (!shouldReadBody(status, ct, htmlOnly)) {
                return resp.releaseBody().thenReturn(new RawResponse(status, ct, location, new byte[0]));
            }
            long length = resp.headers().contentLength().orElse(-1L);
            if (length > maxBytes) {
                return resp.releaseBody().then(Mono.error(new PageTooLargeException(maxBytes)));
            }
            return resp.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(body -> new RawResponse(status, ct, location, body));
        }).block(blockTimeout);
    }
}
