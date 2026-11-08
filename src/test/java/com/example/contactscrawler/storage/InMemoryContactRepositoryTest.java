package com.example.contactscrawler.storage;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryContactRepositoryTest {

    @RepeatedTest(10)
    void concurrentUpsertHasNoDuplicatesAndKeepsMaxLastSeen() {
        InMemoryContactRepository repo = new InMemoryContactRepository();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            Instant seen = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(t);
            futures.add(CompletableFuture.runAsync(() -> repo.saveAll(RepositoryTestData.contacts(500, seen)), pool));
        }
        futures.forEach(CompletableFuture::join);
        pool.shutdown();
        assertThat(repo.count()).isEqualTo(500);
        assertThat(repo.findAll()).extracting(Contact::id).doesNotHaveDuplicates();
        assertThat(repo.findAll()).allSatisfy(c ->
                assertThat(c.lastSeenAt()).isEqualTo(Instant.parse("2026-01-01T00:00:15Z")));
    }

    @Test
    void filterOrganizationsDelete() {
        InMemoryContactRepository repo = new InMemoryContactRepository();
        repo.saveAll(RepositoryTestData.contacts(60, Instant.now()));
        assertThat(repo.findByFilter(ContactType.PHONE, null)).hasSize(10);
        assertThat(repo.findByFilter(null, "d1.ru")).hasSize(12);
        assertThat(repo.findByFilter(ContactType.PHONE, "d0.ru")).allMatch(c -> c.domain().equals("d0.ru"));
        repo.saveOrganization("d1.ru", "Org", "https://d1.ru");
        repo.saveOrganization("d1.ru", "Other", "https://d1.ru");
        assertThat(repo.organizationNames()).containsEntry("d1.ru", "Org");
        repo.deleteAll();
        assertThat(repo.count()).isZero();
        assertThat(repo.organizationNames()).isEmpty();
    }
}
