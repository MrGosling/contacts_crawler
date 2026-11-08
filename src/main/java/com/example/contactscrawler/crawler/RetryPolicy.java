package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.crawler.model.FetchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Повторы загрузки: до crawler.retries раз, backoff retryBackoff * 2^n (1 с, 2 с);
 * только таймауты/ошибки соединения (httpStatus=0), 429 и 5xx (D-10).
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final int retries;
    private final Duration backoff;

    @org.springframework.beans.factory.annotation.Autowired
    public RetryPolicy(CrawlerProperties props) {
        this(props.getRetries(), props.getRetryBackoff());
    }

    public RetryPolicy(int retries, Duration backoff) {
        this.retries = retries;
        this.backoff = backoff;
    }

    public FetchResult execute(Supplier<FetchResult> call) throws InterruptedException {
        FetchResult result = call.get();
        for (int attempt = 0; attempt < retries && isRetryable(result); attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Retry interrupted");
            }
            long wait = backoff.toMillis() << attempt;
            log.debug("Retry {} for {} in {} ms ({})", attempt + 1, result.requestedUrl(), wait, result.error());
            TimeUnit.MILLISECONDS.sleep(wait);
            result = call.get();
        }
        return result;
    }

    public boolean isRetryable(FetchResult r) {
        if (r.status() != FetchStatus.FAILED) {
            return false;
        }
        int s = r.httpStatus();
        return s == 0 || s == 429 || s >= 500;
    }
}
