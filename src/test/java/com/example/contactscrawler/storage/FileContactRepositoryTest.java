package com.example.contactscrawler.storage;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FileContactRepositoryTest {

    @TempDir
    Path tmp;

    private FileContactRepository repo(Path file, Duration interval) {
        FileContactRepository r = new FileContactRepository(file, interval);
        r.init();
        return r;
    }

    @RepeatedTest(10)
    void concurrentWritesAndReadsThenRestore() throws Exception {
        Path file = tmp.resolve("c.jsonl");
        FileContactRepository repo = repo(file, Duration.ofMillis(20));
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Instant seen = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(t);
            futures.add(CompletableFuture.runAsync(() -> repo.saveAll(RepositoryTestData.contacts(300, seen)), pool));
        }
        for (int t = 0; t < 4; t++) {
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 50; i++) {
                    assertThat(repo.findAll().size()).isLessThanOrEqualTo(300);
                    repo.findByFilter(ContactType.EMAIL, "d1.ru");
                }
            }, pool));
        }
        futures.forEach(CompletableFuture::join);
        pool.shutdown();
        repo.close(); // финальный flush
        assertThat(repo.count()).isEqualTo(300);
        assertThat(Files.readAllLines(file)).hasSize(300);

        FileContactRepository restored = repo(file, Duration.ofSeconds(10));
        assertThat(restored.count()).isEqualTo(300);
        assertThat(restored.findAll()).extracting(Contact::key)
                .containsExactlyInAnyOrderElementsOf(repo.findAll().stream().map(Contact::key).toList());
        restored.saveAll(RepositoryTestData.contacts(301, Instant.now()));
        assertThat(restored.bufferedCount()).isEqualTo(1);
        restored.close();
        assertThat(Files.readAllLines(file)).hasSize(301);
    }

    @Test
    void daemonFlushesPeriodically() {
        Path file = tmp.resolve("d.jsonl");
        FileContactRepository repo = repo(file, Duration.ofMillis(30));
        assertThat(repo.daemon().isDaemon()).isTrue();
        repo.saveAll(RepositoryTestData.contacts(10, Instant.now()));
        await().atMost(Duration.ofSeconds(5)).until(() -> Files.exists(file) && Files.readAllLines(file).size() == 10);
        repo.close();
        assertThat(repo.daemon().isAlive()).isFalse();
    }

    @Test
    void corruptedLastLineIsSkippedAndTruncated() throws Exception {
        Path file = tmp.resolve("broken.jsonl");
        FileContactRepository first = repo(file, Duration.ofSeconds(10));
        first.saveAll(RepositoryTestData.contacts(2, Instant.now()));
        first.close();
        Files.writeString(file, "{\"id\":3,\"type\":\"PHO", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        FileContactRepository repo = repo(file, Duration.ofSeconds(10));
        assertThat(repo.count()).isEqualTo(2);
        repo.saveAll(List.of(RepositoryTestData.contact(7, Instant.now())));
        repo.close();
        assertThat(Files.readAllLines(file)).hasSize(3);
        assertThat(repo(file, Duration.ofSeconds(10)).count()).isEqualTo(3);
    }

    @Test
    void corruptedMiddleLineAndMissingTrailingNewline() throws Exception {
        Path file = tmp.resolve("mid.jsonl");
        FileContactRepository first = repo(file, Duration.ofSeconds(10));
        first.saveAll(RepositoryTestData.contacts(1, Instant.now()));
        first.close();
        String valid = Files.readString(file).trim();
        Files.writeString(file, "garbage\n" + valid);
        FileContactRepository repo = repo(file, Duration.ofSeconds(10));
        assertThat(repo.count()).isEqualTo(1);
        repo.saveAll(List.of(RepositoryTestData.contact(5, Instant.now())));
        repo.close();
        assertThat(repo(file, Duration.ofSeconds(10)).count()).isEqualTo(2);
    }

    @Test
    void deleteAllTruncatesFile() throws Exception {
        Path file = tmp.resolve("x/del.jsonl");
        FileContactRepository repo = repo(file, Duration.ofSeconds(10));
        repo.saveAll(RepositoryTestData.contacts(5, Instant.now()));
        repo.flush();
        repo.saveOrganization("d1.ru", "Org", null);
        assertThat(repo.organizationNames()).containsKey("d1.ru");
        repo.deleteAll();
        assertThat(repo.count()).isZero();
        assertThat(Files.size(file)).isZero();
        assertThat(repo.organizationNames()).isEmpty();
        repo.close();
    }
}
