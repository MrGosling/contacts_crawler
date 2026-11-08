package com.example.contactscrawler.crawler.fetch;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.crawler.model.FetchStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общая логика для всех PageFetcher: редиректы (до crawler.max-redirects, клиенты сами не следуют),
 * проверка статуса и Content-Type, лимит размера, определение кодировки (заголовок → &lt;meta charset&gt; → UTF-8).
 * Реализация выполняет ровно один HTTP-запрос в {@link #execute}.
 */
public abstract class AbstractPageFetcher implements PageFetcher {

    private static final Pattern HEADER_CHARSET = Pattern.compile("charset=\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?\\s*([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    protected final CrawlerProperties props;
    protected final long maxBytes;

    protected AbstractPageFetcher(CrawlerProperties props) {
        this.props = props;
        this.maxBytes = props.getMaxPageSize().toBytes();
    }

    /** Один ответ HTTP. body пустое, если тело не читалось. */
    protected record RawResponse(int status, String contentType, String location, byte[] body) {
    }

    /**
     * Выполнить один GET без следования редиректам. Тело читать только если {@link #shouldReadBody};
     * при превышении лимита — бросить {@link PageTooLargeException}.
     */
    protected abstract RawResponse execute(URI url, boolean htmlOnly) throws Exception;

    @Override
    public FetchResult fetch(URI url) {
        return doFetch(url, true);
    }

    @Override
    public FetchResult fetchText(URI url) {
        return doFetch(url, false);
    }

    private FetchResult doFetch(URI url, boolean htmlOnly) {
        long start = System.nanoTime();
        URI current = url;
        String contentType = null;
        try {
            for (int redirects = 0; ; redirects++) {
                RawResponse r = execute(current, htmlOnly);
                contentType = r.contentType();
                if (isRedirect(r.status()) && r.location() != null && !r.location().isBlank()) {
                    if (redirects >= props.getMaxRedirects()) {
                        return FetchResult.failure(url, current, r.status(), FetchStatus.FAILED, contentType,
                                since(start), "Too many redirects");
                    }
                    current = current.resolve(r.location().trim());
                    continue;
                }
                if (r.status() < 200 || r.status() >= 300) {
                    return FetchResult.failure(url, current, r.status(), FetchStatus.FAILED, contentType,
                            since(start), "HTTP " + r.status());
                }
                if (htmlOnly && !isHtml(contentType)) {
                    return FetchResult.failure(url, current, r.status(), FetchStatus.NOT_HTML, contentType,
                            since(start), "Content-Type " + contentType);
                }
                return FetchResult.ok(url, current, r.status(), contentType, decode(r.body(), contentType), since(start));
            }
        } catch (PageTooLargeException e) {
            return FetchResult.failure(url, current, 200, FetchStatus.TOO_LARGE, contentType, since(start), e.getMessage());
        } catch (Exception e) {
            if (findCause(e, InterruptedException.class) != null) {
                Thread.currentThread().interrupt(); // восстановить флаг: вызывающий должен завершиться
            }
            PageTooLargeException tooLarge = findCause(e, PageTooLargeException.class);
            if (tooLarge != null || isBufferLimit(e)) {
                return FetchResult.failure(url, current, 200, FetchStatus.TOO_LARGE, contentType, since(start),
                        "Response body exceeds " + maxBytes + " bytes");
            }
            return FetchResult.failure(url, current, 0, FetchStatus.FAILED, contentType, since(start),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    protected boolean shouldReadBody(int status, String contentType, boolean htmlOnly) {
        return status >= 200 && status < 300 && (!htmlOnly || isHtml(contentType));
    }

    protected static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    public static boolean isHtml(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("text/html") || ct.startsWith("application/xhtml+xml");
    }

    /** Чтение потока с ограничением размера. */
    protected byte[] readLimited(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new PageTooLargeException(maxBytes);
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    protected void checkContentLength(long contentLength) {
        if (contentLength > maxBytes) {
            throw new PageTooLargeException(maxBytes);
        }
    }

    static String decode(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = null;
        if (contentType != null) {
            Matcher m = HEADER_CHARSET.matcher(contentType);
            if (m.find()) {
                charset = charsetOrNull(m.group(1));
            }
        }
        if (charset == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            if (m.find()) {
                charset = charsetOrNull(m.group(1));
            }
        }
        return new String(body, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    private static Charset charsetOrNull(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static Duration since(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static boolean isBufferLimit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getClass().getSimpleName().equals("DataBufferLimitException")) {
                return true;
            }
        }
        return false;
    }
}
