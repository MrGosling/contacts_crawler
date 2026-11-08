package com.example.contactscrawler.crawler.scheduler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.CrawlParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Источник seed-URL: crawler.seeds + seeds-file (read-only) + seed'ы из REST (CopyOnWriteArrayList,
 * персист в crawler.seeds-api-file — see Q-8).
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final CrawlerProperties props;
    private final Path apiFile;
    private final CopyOnWriteArrayList<URI> configSeeds = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<URI> apiSeeds = new CopyOnWriteArrayList<>();
    private final Object fileLock = new Object();

    public SeedService(CrawlerProperties props) {
        this.props = props;
        this.apiFile = Path.of(props.getSeedsApiFile());
    }

    @PostConstruct
    public void load() {
        configSeeds.clear();
        props.getSeeds().forEach(s -> parse(s, "crawler.seeds").ifPresent(configSeeds::addIfAbsent));
        if (props.getSeedsFile() != null && !props.getSeedsFile().isBlank()) {
            readFile(Path.of(props.getSeedsFile())).forEach(configSeeds::addIfAbsent);
        }
        apiSeeds.clear();
        readFile(apiFile).forEach(apiSeeds::addIfAbsent);
        log.info("Loaded {} configured and {} API seed URLs", configSeeds.size(), apiSeeds.size());
    }

    public List<URI> all() {
        Set<URI> all = new LinkedHashSet<>(configSeeds);
        all.addAll(apiSeeds);
        return List.copyOf(all);
    }

    /** Добавляет URL (невалидные → IllegalArgumentException) и дописывает новые в seeds-api-файл. */
    public List<URI> add(List<String> urls) {
        List<URI> parsed = urls.stream().map(CrawlParams::parseHttpUri).toList();
        List<URI> added = new ArrayList<>();
        for (URI u : parsed) {
            if (apiSeeds.addIfAbsent(u)) {
                added.add(u);
            }
        }
        if (!added.isEmpty()) {
            synchronized (fileLock) {
                try {
                    if (apiFile.getParent() != null) {
                        Files.createDirectories(apiFile.getParent());
                    }
                    List<String> lines = added.stream().map(URI::toString).toList();
                    Files.write(apiFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    log.warn("Cannot persist API seeds to {}: {}", apiFile, e.toString());
                }
            }
        }
        return all();
    }

    public void clearApiSeeds() {
        apiSeeds.clear();
        synchronized (fileLock) {
            try {
                Files.deleteIfExists(apiFile);
            } catch (IOException e) {
                log.warn("Cannot delete {}: {}", apiFile, e.toString());
            }
        }
    }

    private List<URI> readFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .flatMap(l -> parse(l, path.toString()).stream())
                    .toList();
        } catch (IOException e) {
            log.warn("Cannot read seeds file {}: {}", path, e.toString());
            return List.of();
        }
    }

    private static java.util.Optional<URI> parse(String raw, String source) {
        try {
            return java.util.Optional.of(CrawlParams.parseHttpUri(raw));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid seed URL '{}' in {}", raw, source);
            return java.util.Optional.empty();
        }
    }
}
