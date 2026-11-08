package com.example.contactscrawler.crawler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();
    private final URI base = URI.create("https://a.ru/dir/page");

    @ParameterizedTest
    @CsvSource({
            "HTTPS://WWW.A.RU:443/Path/,https://www.a.ru/Path",
            "http://a.ru:80,http://a.ru/",
            "https://a.ru/,https://a.ru/",
            "http://a.ru:8080/x#frag,http://a.ru:8080/x",
            "https://a.ru/x?utm_source=1&b=2&gclid=3&a=1&yclid=4&fbclid=5&UTM_medium=6,https://a.ru/x?a=1&b=2",
            "/contacts,https://a.ru/contacts",
            "other,https://a.ru/dir/other",
            "../up/,https://a.ru/up",
            "//cdn.a.ru/x,https://cdn.a.ru/x",
            "https://a.ru/путь с пробелом,https://a.ru/%D0%BF%D1%83%D1%82%D1%8C%20%D1%81%20%D0%BF%D1%80%D0%BE%D0%B1%D0%B5%D0%BB%D0%BE%D0%BC"
    })
    void normalizes(String href, String expected) {
        assertThat(normalizer.normalize(href, base)).contains(URI.create(expected));
    }

    @ParameterizedTest
    @CsvSource({"javascript:void(0)", "mailto:a@b.ru", "tel:+7495", "ftp://a.ru/x", "data:text/plain,x", "http://"})
    void rejects(String href) {
        assertThat(normalizer.normalize(href, base)).isEmpty();
    }

    @Test
    void blankAndNull() {
        assertThat(normalizer.normalize("  ", base)).isEmpty();
        assertThat(normalizer.normalize((String) null, base)).isEmpty();
        assertThat(normalizer.normalize((URI) null)).isEqualTo(Optional.empty());
        assertThat(normalizer.normalize("https://a.ru/x", null)).contains(URI.create("https://a.ru/x"));
    }

    @Test
    void domainOf() {
        assertThat(UrlNormalizer.domainOf(URI.create("https://WWW.A.ru/x"))).isEqualTo("a.ru");
        assertThat(UrlNormalizer.domainOf(URI.create("https://shop.a.ru/x"))).isEqualTo("shop.a.ru");
        assertThat(UrlNormalizer.domainOf((String) null)).isEmpty();
    }
}
