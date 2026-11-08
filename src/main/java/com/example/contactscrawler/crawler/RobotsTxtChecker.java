package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.model.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

/**
 * Загружает и кеширует robots.txt на хост в рамках задания. Кеш — ConcurrentHashMap&lt;String, FutureTask&gt;:
 * загрузку выполняет первый обратившийся поток вне блокировок карты (нет удержания lock во время сетевого I/O),
 * остальные ждут тот же FutureTask. 2xx → правила; 4xx → всё разрешено; 5xx/таймаут → хост запрещён.
 */
@Component
public class RobotsTxtChecker {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtChecker.class);

    private final PageFetcher fetcher;
    private final String userAgentToken;
    private final ConcurrentHashMap<String, FutureTask<RobotsRules>> cache = new ConcurrentHashMap<>();

    public RobotsTxtChecker(PageFetcher fetcher, CrawlerProperties props) {
        this.fetcher = fetcher;
        String ua = props.getUserAgent().toLowerCase(Locale.ROOT);
        this.userAgentToken = ua.split("[/\\s]")[0];
    }

    public boolean isAllowed(URI url) {
        String path = (url.getRawPath() == null || url.getRawPath().isEmpty() ? "/" : url.getRawPath())
                + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery());
        return rulesFor(url).isAllowed(path);
    }

    public Optional<Duration> crawlDelay(URI url) {
        return Optional.ofNullable(rulesFor(url).crawlDelay());
    }

    public void reset() {
        cache.clear();
    }

    RobotsRules rulesFor(URI url) {
        String key = originOf(url);
        FutureTask<RobotsRules> task = cache.get(key);
        if (task == null) {
            FutureTask<RobotsRules> created = new FutureTask<>(() -> load(key));
            task = cache.putIfAbsent(key, created);
            if (task == null) {
                task = created;
                created.run();
            }
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RobotsRules.DENY_ALL;
        } catch (ExecutionException e) {
            return RobotsRules.DENY_ALL;
        }
    }

    private RobotsRules load(String origin) {
        FetchResult r = fetcher.fetchText(URI.create(origin + "/robots.txt"));
        if (r.isOk()) {
            return parse(r.body(), userAgentToken);
        }
        int s = r.httpStatus();
        if (s >= 400 && s < 500) {
            return RobotsRules.ALLOW_ALL;
        }
        log.info("robots.txt for {} unavailable ({}): host disallowed for this job", origin, r.error());
        return RobotsRules.DENY_ALL;
    }

    static String originOf(URI url) {
        String scheme = url.getScheme().toLowerCase(Locale.ROOT);
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return scheme + "://" + host + (url.getPort() == -1 ? "" : ":" + url.getPort());
    }

    /** Разбор robots.txt: группы User-agent, Allow/Disallow (wildcards * и $), Crawl-delay. */
    public static RobotsRules parse(String content, String agentToken) {
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasAgent = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "user-agent" -> {
                    if (current == null || !lastWasAgent) {
                        current = new Group();
                        groups.add(current);
                    }
                    current.agents.add(value.toLowerCase(Locale.ROOT));
                    lastWasAgent = true;
                }
                case "allow", "disallow" -> {
                    lastWasAgent = false;
                    if (current != null && !value.isEmpty()) {
                        current.rules.add(new Rule(key.equals("allow"), toPattern(value), value.length()));
                    }
                }
                case "crawl-delay" -> {
                    lastWasAgent = false;
                    if (current != null) {
                        try {
                            current.crawlDelay = Duration.ofMillis((long) (Double.parseDouble(value) * 1000));
                        } catch (NumberFormatException ignored) {
                            // некорректный Crawl-delay игнорируется
                        }
                    }
                }
                default -> lastWasAgent = false;
            }
        }
        Group selected = null;
        for (Group g : groups) {
            if (g.agents.stream().anyMatch(a -> !a.equals("*") && agentToken.contains(a))) {
                selected = g;
                break;
            }
        }
        if (selected == null) {
            selected = groups.stream().filter(g -> g.agents.contains("*")).findFirst().orElse(null);
        }
        return selected == null ? RobotsRules.ALLOW_ALL : new RobotsRules(List.copyOf(selected.rules), selected.crawlDelay);
    }

    private static Pattern toPattern(String value) {
        boolean anchored = value.endsWith("$");
        String v = anchored ? value.substring(0, value.length() - 1) : value;
        StringBuilder regex = new StringBuilder();
        for (String part : v.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (anchored) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString());
    }

    private static final class Group {
        final Set<String> agents = new LinkedHashSet<>();
        final List<Rule> rules = new ArrayList<>();
        Duration crawlDelay;
    }

    record Rule(boolean allow, Pattern pattern, int length) {
    }

    /** Правила одного хоста. Побеждает самое длинное совпадение; при равенстве — Allow. */
    public record RobotsRules(List<Rule> rules, Duration crawlDelay, boolean denyAll) {

        static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), null, false);
        static final RobotsRules DENY_ALL = new RobotsRules(List.of(), null, true);

        RobotsRules(List<Rule> rules, Duration crawlDelay) {
            this(rules, crawlDelay, false);
        }

        public boolean isAllowed(String path) {
            if (denyAll) {
                return false;
            }
            Rule best = null;
            for (Rule r : rules) {
                if (r.pattern().matcher(path).lookingAt()
                        && (best == null || r.length() > best.length() || (r.length() == best.length() && r.allow()))) {
                    best = r;
                }
            }
            return best == null || best.allow();
        }
    }
}
