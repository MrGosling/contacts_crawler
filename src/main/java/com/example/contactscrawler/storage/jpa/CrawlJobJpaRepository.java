package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.crawler.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data репозиторий заданий.
 */
public interface CrawlJobJpaRepository extends JpaRepository<CrawlJobEntity, String> {

    List<CrawlJobEntity> findByStatusIn(Collection<JobStatus> statuses);
}
