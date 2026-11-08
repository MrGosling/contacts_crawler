package com.example.contactscrawler.crawler;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Нормализация URL (docs 03-crawling.md п.5): абсолютный URL, lower-case схема/хост, без порта по умолчанию,
 * без #fragment, без utm_*, gclid, yclid, fbclid, сортировка параметров, удаление завершающего '/' кроме корня (D-5).
 */
@Component
public class UrlNormalizer {

    private static final Set<String> DROPPED_PARAMS = Set.of("gclid", "yclid", "fbclid");
    private static final Set<String> IGNORED_SCHEMES = Set.of("javascript", "mailto", "tel", "data", "ftp", "file");

    public Optional<URI> normalize(String href, URI base) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String h = href.trim();
        int colon = h.indexOf(':');
        if (colon > 0 && IGNORED_SCHEMES.contains(h.substring(0, colon).toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        try {
            URI ref = new URI(encodeUnsafe(h));
            URI resolved = base == null ? ref : base.resolve(ref);
            return normalize(resolved);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<URI> normalize(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return Optional.empty();
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }
        String path = uri.normalize().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String query = normalizeQuery(uri.getRawQuery());
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (port != -1) {
            sb.append(':').append(port);
        }
        sb.append(path);
        if (!query.isEmpty()) {
            sb.append('?').append(query);
        }
        try {
            return Optional.of(new URI(sb.toString()));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        return Arrays.stream(rawQuery.split("&"))
                .filter(p -> !p.isEmpty())
                .filter(p -> {
                    String name = (p.contains("=") ? p.substring(0, p.indexOf('=')) : p).toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !DROPPED_PARAMS.contains(name);
                })
                .sorted()
                .collect(Collectors.joining("&"));
    }

    /** Кодирует пробелы, не-ASCII и прочие недопустимые в URI символы (кроме '%'). */
    static String encodeUnsafe(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c > 127 || c <= 32 || "<>\"{}|\\^`".indexOf(c) >= 0) {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(String.format("%02X", b & 0xff));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** D-6: хост страницы в нижнем регистре без {@code www.}. */
    public static String domainOf(URI uri) {
        return domainOf(uri == null ? null : uri.getHost());
    }

    public static String domainOf(String host) {
        if (host == null) {
            return "";
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }
}
