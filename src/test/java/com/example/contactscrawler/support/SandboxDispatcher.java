package com.example.contactscrawler.support;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Генерируемый sandbox: дерево страниц /, /p1, /p2 ... (heap-нумерация, branching детей на узел, depth уровней).
 * Каждая страница содержит уникальный email, ссылки на детей, дубликаты (#fragment, /), ссылку в /private
 * (запрещена robots.txt), pdf и внешний домен. Считает число запросов на каждый путь.
 */
public class SandboxDispatcher extends Dispatcher {

    private final int branching;
    private final int total;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private volatile CountDownLatch gate;
    private final AtomicInteger inFlight = new AtomicInteger();

    public SandboxDispatcher(int branching, int depth) {
        this.branching = branching;
        int t = 0;
        int level = 1;
        for (int d = 0; d <= depth; d++) {
            t += level;
            level *= branching;
        }
        this.total = t;
    }

    /** Все страницы (кроме robots.txt) блокируются до release(). */
    public SandboxDispatcher blocking() {
        gate = new CountDownLatch(1);
        return this;
    }

    public void release() {
        if (gate != null) {
            gate.countDown();
        }
    }

    public int totalPages() {
        return total;
    }

    public int hits(String path) {
        AtomicInteger a = hits.get(path);
        return a == null ? 0 : a.get();
    }

    public Map<String, AtomicInteger> allHits() {
        return hits;
    }

    public int inFlight() {
        return inFlight.get();
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        String path = request.getPath() == null ? "/" : request.getPath();
        hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        if (path.equals("/robots.txt")) {
            return new MockResponse().setHeader("Content-Type", "text/plain")
                    .setBody("User-agent: *\nDisallow: /private\n");
        }
        CountDownLatch g = gate;
        if (g != null) {
            inFlight.incrementAndGet();
            try {
                g.await(30, TimeUnit.SECONDS);
            } finally {
                inFlight.decrementAndGet();
            }
        }
        int id;
        if (path.equals("/")) {
            id = 0;
        } else if (path.matches("/p\\d+")) {
            id = Integer.parseInt(path.substring(2));
        } else {
            return new MockResponse().setResponseCode(404);
        }
        if (id >= total) {
            return new MockResponse().setResponseCode(404);
        }
        StringBuilder sb = new StringBuilder("<html><head><title>Page ").append(id).append("</title></head><body>");
        sb.append("<p>Email: info").append(id).append("@sandbox-org.ru</p>");
        for (int c = 1; c <= branching; c++) {
            int child = id * branching + c;
            if (child < total) {
                sb.append("<a href=\"/p").append(child).append("\">c</a>");
                sb.append("<a href=\"/p").append(child).append("#frag\">dup</a>");
            }
        }
        sb.append("<a href=\"/\">home</a><a href=\"/private/secret\">x</a><a href=\"/file.pdf\">pdf</a>")
                .append("<a href=\"https://external.invalid/\">ext</a><a href=\"mailto:x@sandbox-org.ru\">m</a>");
        sb.append("</body></html>");
        return new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(sb.toString());
    }
}
