package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.config.ExecutorConfig;
import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelExtractionServiceTest {

    private final ForkJoinPool pool = ExecutorConfig.newForkJoinPool(4);
    private final ParallelExtractionService service =
            new ParallelExtractionService(TestSupport.extraction(TestSupport.props()), pool);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private static List<FetchResult> pages(int n) {
        List<FetchResult> pages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            URI u = URI.create("https://site" + (i % 7) + ".ru/p" + i);
            String body = "<html><body><p>Тел: +7 (495) 100-" + String.format("%02d-%02d", i % 100, (i * 7) % 100)
                    + "</p><a href='mailto:mail" + i + "@site" + (i % 7) + ".ru'>m</a></body></html>";
            pages.add(i % 10 == 9
                    ? FetchResult.skippedByRobots(u)
                    : FetchResult.ok(u, u, 200, "text/html", body, Duration.ZERO));
        }
        return pages;
    }

    @RepeatedTest(10)
    void parallelResultEqualsSequential() {
        List<FetchResult> pages = pages(57);
        List<Contact> parallel = service.extractAll(pages);
        List<Contact> sequential = service.extractAllSequential(pages);
        assertThat(parallel).extracting(Contact::key).containsExactlyElementsOf(
                sequential.stream().map(Contact::key).toList());
        assertThat(parallel).isNotEmpty();
    }

    @Test
    void emptyInput() {
        assertThat(service.extractResults(List.of())).isEmpty();
        assertThat(service.extractAll(List.of())).isEmpty();
    }
}
