package com.example.contactscrawler.crawler;

import com.example.contactscrawler.crawler.model.CrawlParams;
import com.example.contactscrawler.support.TestSupport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UrlFilterTest {

    private final UrlFilter filter = new UrlFilter(TestSupport.props());
    private final CrawlParams same = new CrawlParams(List.of(URI.create("https://www.a.ru/")), 2, 100, true, 0);
    private final CrawlParams any = new CrawlParams(List.of(URI.create("https://a.ru/")), 2, 100, false, 0);

    @Test
    void sameDomainIncludesSubdomainsAndWww() {
        assertThat(filter.accept(URI.create("https://a.ru/x"), 1, same)).isTrue();
        assertThat(filter.accept(URI.create("https://shop.a.ru/x"), 1, same)).isTrue();
        assertThat(filter.accept(URI.create("https://b.ru/x"), 1, same)).isFalse();
        assertThat(filter.accept(URI.create("https://evil-a.ru/x"), 1, same)).isFalse();
        assertThat(filter.accept(URI.create("https://b.ru/x"), 1, any)).isTrue();
    }

    @Test
    void depthSchemeExtensions() {
        assertThat(filter.accept(URI.create("https://a.ru/x"), 3, same)).isFalse();
        assertThat(filter.accept(URI.create("ftp://a.ru/x"), 1, same)).isFalse();
        assertThat(filter.accept(URI.create("https://a.ru/doc.PDF"), 1, same)).isFalse();
        assertThat(filter.accept(URI.create("https://a.ru/img/logo.webp"), 1, same)).isFalse();
        assertThat(filter.accept(URI.create("https://a.ru/page.html"), 1, same)).isTrue();
        assertThat(filter.accept(null, 1, same)).isFalse();
    }

    @Test
    void priority() {
        assertThat(filter.priorityOf(URI.create("https://a.ru/kontakty"))).isEqualTo(UrlFilter.HIGH_PRIORITY);
        assertThat(filter.priorityOf(URI.create("https://a.ru/o-kompanii/rekvizity"))).isEqualTo(UrlFilter.HIGH_PRIORITY);
        assertThat(filter.priorityOf(URI.create("https://a.ru/About"))).isEqualTo(UrlFilter.HIGH_PRIORITY);
        assertThat(filter.priorityOf(URI.create("https://a.ru/catalog"))).isEqualTo(UrlFilter.NORMAL_PRIORITY);
    }
}
