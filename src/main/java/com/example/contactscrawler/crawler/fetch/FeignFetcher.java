package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import feign.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

/**
 * Реализация PageFetcher на OpenFeign (crawler.fetcher=feign) через RawPageClient (Q-9).
 */
@Component
@ConditionalOnProperty(name = "crawler.fetcher", havingValue = "feign")
public class FeignFetcher extends AbstractPageFetcher {

    private final RawPageClient client;

    public FeignFetcher(RawPageClient client, CrawlerProperties props) {
        super(props);
        this.client = client;
    }

    @Override
    protected RawResponse execute(URI url, boolean htmlOnly) throws Exception {
        try (Response resp = client.get(url)) {
            int status = resp.status();
            String ct = header(resp.headers(), HttpHeaders.CONTENT_TYPE);
            String location = header(resp.headers(), HttpHeaders.LOCATION);
            if (!shouldReadBody(status, ct, htmlOnly) || resp.body() == null) {
                return new RawResponse(status, ct, location, new byte[0]);
            }
            Integer length = resp.body().length();
            if (length != null) {
                checkContentLength(length);
            }
            return new RawResponse(status, ct, location, readLimited(resp.body().asInputStream()));
        }
    }

    private static String header(Map<String, Collection<String>> headers, String name) {
        for (Map.Entry<String, Collection<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                return e.getValue().iterator().next();
            }
        }
        return null;
    }
}
