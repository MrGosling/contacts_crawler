package com.example.contactscrawler.crawler.model;

import com.example.contactscrawler.api.dto.CrawlRequest;
import com.example.contactscrawler.config.CrawlerProperties;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Неизменяемые параметры задания. Невалидные значения → {@link IllegalArgumentException} (→ 400).
 */
public record CrawlParams(List<URI> seeds, int maxDepth, int maxPagesPerDomain, boolean sameDomainOnly, long delayMs) {

    public CrawlParams {
        seeds = List.copyOf(seeds);
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("At least one seed URL is required");
        }
        if (maxDepth < 0 || maxDepth > 5) {
            throw new IllegalArgumentException("maxDepth must be in 0..5");
        }
        if (maxPagesPerDomain < 1 || maxPagesPerDomain > 10_000) {
            throw new IllegalArgumentException("maxPagesPerDomain must be in 1..10000");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
    }

    public static CrawlParams of(CrawlRequest request, CrawlerProperties props) {
        List<URI> seeds = request.urls().stream().map(CrawlParams::parseHttpUri).toList();
        long delay = request.delayMs() != null ? request.delayMs() : props.getDelayMs();
        if (delay < props.getMinDelayMs()) {
            throw new IllegalArgumentException("delayMs must be >= " + props.getMinDelayMs());
        }
        return new CrawlParams(seeds,
                request.maxDepth() != null ? request.maxDepth() : props.getMaxDepth(),
                request.maxPagesPerDomain() != null ? request.maxPagesPerDomain() : props.getMaxPagesPerDomain(),
                request.sameDomainOnly() != null ? request.sameDomainOnly() : props.isSameDomainOnly(),
                delay);
    }

    /** Параметры по умолчанию (плановый запуск). */
    public static CrawlParams defaults(List<URI> seeds, CrawlerProperties props) {
        return new CrawlParams(seeds, props.getMaxDepth(), props.getMaxPagesPerDomain(), props.isSameDomainOnly(),
                Math.max(props.getDelayMs(), props.getMinDelayMs()));
    }

    public static URI parseHttpUri(String raw) {
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("Only http/https URLs are allowed: " + raw);
            }
            return uri;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: " + raw, e);
        }
    }
}
