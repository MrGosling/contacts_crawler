package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Ограничение запросов к хосту: Semaphore(perHostConcurrency) + время следующего разрешённого запроса.
 * Пауза = max(delayMs, Crawl-delay) (вычисляет вызывающий). Единственное место, где допустим sleep (rate limit).
 */
@Component
public class HostRateLimiter {

    private final int perHostConcurrency;
    private final ConcurrentHashMap<String, HostSlot> slots = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public HostRateLimiter(CrawlerProperties props) {
        this(props.getPerHostConcurrency());
    }

    public HostRateLimiter(int perHostConcurrency) {
        this.perHostConcurrency = perHostConcurrency;
    }

    public Permit acquire(String host, Duration delay) throws InterruptedException {
        HostSlot slot = slots.computeIfAbsent(host, h -> new HostSlot(perHostConcurrency));
        slot.semaphore.acquire();
        try {
            long waitNanos = slot.reserve(delay.toNanos());
            if (waitNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            }
        } catch (InterruptedException e) {
            slot.semaphore.release();
            throw e;
        }
        return new Permit(slot.semaphore);
    }

    public int availablePermits(String host) {
        HostSlot slot = slots.get(host);
        return slot == null ? perHostConcurrency : slot.semaphore.availablePermits();
    }

    private static final class HostSlot {
        final Semaphore semaphore;
        private long nextAllowedNanos;
        private boolean used;

        HostSlot(int permits) {
            this.semaphore = new Semaphore(permits, true);
        }

        /** Резервирует слот времени; возвращает, сколько ждать. */
        synchronized long reserve(long delayNanos) {
            long now = System.nanoTime();
            long start = (!used || now - nextAllowedNanos >= 0) ? now : nextAllowedNanos;
            used = true;
            nextAllowedNanos = start + delayNanos;
            return start - now;
        }
    }

    /** Разрешение на запрос; release в close(). */
    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
