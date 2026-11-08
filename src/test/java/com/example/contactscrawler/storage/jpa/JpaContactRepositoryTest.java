package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.RepositoryTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaContactRepository.class, JpaPageRecorder.class})
@TestPropertySource(properties = "crawler.storage=h2")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaContactRepositoryTest {

    @Autowired
    JpaContactRepository repo;
    @Autowired
    ContactJpaRepository contacts;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void upsertKeepsFirstSourceAndUpdatesLastSeen() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-03-01T00:00:00Z");
        repo.saveOrganization("d0.ru", "Org 0", "https://d0.ru");
        repo.saveAll(List.of(RepositoryTestData.contact(0, t1)));
        Contact later = new Contact(null, ContactType.PHONE, null, "Value 0", "value-0", "d0.ru",
                "https://d0.ru/other", t2, t2, "job2");
        repo.saveAll(List.of(later));
        List<Contact> all = repo.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).sourceUrl()).isEqualTo("https://d0.ru/p0");
        assertThat(all.get(0).foundAt()).isEqualTo(t1);
        assertThat(all.get(0).lastSeenAt()).isEqualTo(t2);
        assertThat(contacts.findAll().get(0).getOrganization()).isNotNull();
        assertThat(repo.organizationNames()).containsEntry("d0.ru", "Org 0");
    }

    @RepeatedTest(3)
    void concurrentUpsertIsSafe() {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Instant seen = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(t);
            futures.add(CompletableFuture.runAsync(() -> repo.saveAll(RepositoryTestData.contacts(60, seen)), pool));
        }
        futures.forEach(CompletableFuture::join);
        pool.shutdown();
        assertThat(repo.count()).isEqualTo(60);
        assertThat(repo.findByFilter(ContactType.PHONE, null)).hasSize(10);
        assertThat(repo.findByFilter(null, "d1.ru")).hasSize(12);
        assertThat(repo.findByFilter(ContactType.EMAIL, "d1.ru")).allMatch(c -> c.type() == ContactType.EMAIL);
        assertThat(contacts.findByTypeAndDomain(ContactType.PHONE, "d0.ru")).isNotEmpty();
    }

    @Test
    void concurrentOrganizationCreation() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            futures.add(CompletableFuture.runAsync(() -> repo.saveOrganization("x.ru", "X", "https://x.ru"), pool));
        }
        futures.forEach(CompletableFuture::join);
        pool.shutdown();
        assertThat(repo.organizationNames()).hasSize(1);
        repo.saveAll(List.of());
    }
}
