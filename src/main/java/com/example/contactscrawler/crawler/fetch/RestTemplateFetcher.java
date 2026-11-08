package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Реализация PageFetcher на RestTemplate (crawler.fetcher=resttemplate), чтение тела с лимитом размера.
 */
@Component
@ConditionalOnProperty(name = "crawler.fetcher", havingValue = "resttemplate")
public class RestTemplateFetcher extends AbstractPageFetcher {

    private final RestTemplate restTemplate;

    public RestTemplateFetcher(RestTemplate crawlerRestTemplate, CrawlerProperties props) {
        super(props);
        this.restTemplate = crawlerRestTemplate;
    }

    @Override
    protected RawResponse execute(URI url, boolean htmlOnly) {
        return restTemplate.execute(url, HttpMethod.GET,
                req -> req.getHeaders().set(HttpHeaders.USER_AGENT, props.getUserAgent()),
                resp -> {
                    int status = resp.getStatusCode().value();
                    MediaType mt = resp.getHeaders().getContentType();
                    String ct = mt == null ? null : mt.toString();
                    String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (!shouldReadBody(status, ct, htmlOnly)) {
                        return new RawResponse(status, ct, location, new byte[0]);
                    }
                    checkContentLength(resp.getHeaders().getContentLength());
                    return new RawResponse(status, ct, location, readLimited(resp.getBody()));
                });
    }
}
