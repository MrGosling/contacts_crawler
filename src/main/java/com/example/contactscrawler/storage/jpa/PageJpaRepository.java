package com.example.contactscrawler.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data репозиторий страниц.
 */
public interface PageJpaRepository extends JpaRepository<PageEntity, Long> {

    List<PageEntity> findByJobId(String jobId);
}
