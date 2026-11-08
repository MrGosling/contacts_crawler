package com.example.contactscrawler.crawler;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.JobStatus;
import com.example.contactscrawler.storage.jpa.CrawlJobEntity;
import com.example.contactscrawler.storage.jpa.CrawlJobJpaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Задания в памяти (ConcurrentHashMap&lt;jobId, CrawlJob&gt;); при crawler.storage=h2 дублируются в таблицу crawl_job.
 * При старте незавершённые задания из БД помечаются CANCELLED (see Q-7).
 */
@Component
public class CrawlJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(CrawlJobRegistry.class);

    private final ConcurrentHashMap<String, CrawlJob> jobs = new ConcurrentHashMap<>();
    private final CrawlJobJpaRepository jpa;

    public CrawlJobRegistry() {
        this.jpa = null;
    }

    @Autowired
    public CrawlJobRegistry(ObjectProvider<CrawlJobJpaRepository> jpa, CrawlerProperties props) {
        this.jpa = "h2".equalsIgnoreCase(props.getStorage()) ? jpa.getIfAvailable() : null;
    }

    @PostConstruct
    void cancelStaleJobs() {
        if (jpa == null) {
            return;
        }
        try {
            List<CrawlJobEntity> stale = jpa.findByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING));
            stale.forEach(e -> {
                e.setStatus(JobStatus.CANCELLED);
                e.setFinishedAt(Instant.now());
                e.setError("Application restarted");
            });
            jpa.saveAll(stale);
        } catch (RuntimeException e) {
            log.warn("Cannot cancel stale jobs: {}", e.toString());
        }
    }

    public void register(CrawlJob job) {
        jobs.put(job.id(), job);
        persist(job);
    }

    public Optional<CrawlJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<CrawlJob> all() {
        return List.copyOf(jobs.values());
    }

    public void persist(CrawlJob job) {
        if (jpa == null) {
            return;
        }
        try {
            jpa.save(CrawlJobEntity.from(job));
        } catch (RuntimeException e) {
            log.warn("Cannot persist job {}: {}", job.id(), e.toString());
        }
    }
}
