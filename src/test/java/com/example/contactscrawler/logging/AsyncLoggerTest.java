package com.example.contactscrawler.logging;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncLoggerTest {

    @TempDir
    Path tmp;

    @Test
    void preservesOrderAndLosesNothingAfterClose() throws Exception {
        Path file = tmp.resolve("logs/a.log");
        AsyncLogger logger = new AsyncLogger(file, 100_000);
        logger.start();
        logger.start(); // идемпотентно
        assertThat(logger.isWorkerAlive()).isTrue();
        for (int i = 0; i < 20_000; i++) {
            logger.info("msg-" + i);
        }
        logger.close();
        logger.close();
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(20_000);
        for (int i = 0; i < lines.size(); i++) {
            assertThat(lines.get(i)).endsWith(" INFO [" + Thread.currentThread().getName() + "] msg-" + i);
        }
        assertThat(logger.droppedCount()).isZero();
        assertThat(logger.writtenCount()).isEqualTo(20_000);
        assertThat(logger.isWorkerAlive()).isFalse();
        logger.warn("after close");
        assertThat(logger.droppedCount()).isEqualTo(1);
    }

    @RepeatedTest(10)
    void manyProducersKeepPerThreadOrder() throws Exception {
        Path file = tmp.resolve("m.log");
        Files.deleteIfExists(file);
        AsyncLogger logger = new AsyncLogger(file, 100_000);
        logger.start();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int p = 0; p < 8; p++) {
            int id = p;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 1000; i++) {
                    logger.log("INFO", "p" + id + " " + i);
                }
            }, pool));
        }
        futures.forEach(CompletableFuture::join);
        pool.shutdown();
        logger.close();
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(8000);
        Map<String, Integer> last = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.substring(line.indexOf("] ") + 2).split(" ");
            int n = Integer.parseInt(parts[1]);
            assertThat(n).isEqualTo(last.getOrDefault(parts[0], -1) + 1);
            last.put(parts[0], n);
        }
    }

    @Test
    void overflowIsCounted() {
        AsyncLogger logger = new AsyncLogger(tmp.resolve("o.log"), 1);
        // не запущен: потребителя нет, очередь ёмкостью 1 переполняется
        logger.info("1");
        logger.info("2");
        assertThat(logger.droppedCount()).isEqualTo(1);
        logger.close(); // не запущен → no-op
        assertThat(LogEvent.of("INFO", "x").format()).contains("INFO");
    }
}
