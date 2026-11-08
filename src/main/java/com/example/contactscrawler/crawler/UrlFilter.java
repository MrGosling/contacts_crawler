package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.CrawlParams;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Фильтр ссылок: схема http/https, sameDomainOnly (хост seed и поддомены), глубина, исключённые расширения.
 * Посещённость и лимит страниц на домен проверяет {@link VisitedUrls}.
 */
@Component
public class UrlFilter {

    public static final int HIGH_PRIORITY = 10;
    public static final int NORMAL_PRIORITY = 0;
    private static final Pattern PRIORITY = Pattern.compile(
            "contact|kontakt|about|o-kompanii|rekvizity|requisites", Pattern.CASE_INSENSITIVE);

    private final Set<String> excludedExtensions;

    public UrlFilter(CrawlerProperties props) {
        this.excludedExtensions = props.getExcludedExtensions().stream()
                .map(e -> e.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean accept(URI url, int depth, CrawlParams params) {
        if (url == null || depth < 0 || depth > params.maxDepth()) {
            return false;
        }
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        if (hasExcludedExtension(url)) {
            return false;
        }
        return domainAllowed(url, params);
    }

    /** Проверка домена (используется и для конечного URL после редиректа). */
    public boolean domainAllowed(URI url, CrawlParams params) {
        if (!params.sameDomainOnly()) {
            return true;
        }
        String domain = UrlNormalizer.domainOf(url);
        for (URI seed : params.seeds()) {
            String seedDomain = UrlNormalizer.domainOf(seed);
            if (domain.equals(seedDomain) || domain.endsWith("." + seedDomain)) {
                return true;
            }
        }
        return false;
    }

    boolean hasExcludedExtension(URI url) {
        String path = url.getPath();
        if (path == null) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        int dot = last.lastIndexOf('.');
        return dot >= 0 && excludedExtensions.contains(last.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public int priorityOf(URI url) {
        String s = (url.getRawPath() == null ? "" : url.getRawPath())
                + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery());
        return PRIORITY.matcher(s).find() ? HIGH_PRIORITY : NORMAL_PRIORITY;
    }
}
