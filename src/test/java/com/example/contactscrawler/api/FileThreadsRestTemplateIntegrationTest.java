package com.example.contactscrawler.api;

import com.example.contactscrawler.crawler.engine.CrawlEngine;
import com.example.contactscrawler.crawler.engine.ThreadCrawlEngine;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.crawler.fetch.RestTemplateFetcher;
import com.example.contactscrawler.crawler.scheduler.CrawlScheduler;
import com.example.contactscrawler.storage.ContactRepository;
import com.example.contactscrawler.storage.FileContactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** storage=file, engine=threads, fetcher=resttemplate, scheduler=executor. */
@SpringBootTest(properties = {
        "crawler.storage=file", "crawler.engine=threads", "crawler.fetcher=resttemplate",
        "crawler.storage-file.path=target/test-data/it-contacts.jsonl",
        "crawler.scheduler.mode=executor", "crawler.scheduler.initial-delay=PT1H"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileThreadsRestTemplateIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    CrawlEngine engine;
    @Autowired
    PageFetcher fetcher;
    @Autowired
    ContactRepository repository;
    @Autowired
    CrawlScheduler scheduler;

    @Test
    void crawlWithThreadsIntoFile() throws Exception {
        assertThat(engine).isInstanceOf(ThreadCrawlEngine.class);
        assertThat(fetcher).isInstanceOf(RestTemplateFetcher.class);
        assertThat(repository).isInstanceOf(FileContactRepository.class);
        repository.deleteAll();
        crawlSandboxAndWait();
        mvc.perform(get("/answer").param("type", "EMAIL")).andExpect(jsonPath("$.totalElements").value(14));
        mvc.perform(get("/api/crawl/status")).andExpect(jsonPath("$.engine").value("threads"));
        assertThat(scheduler.runs()).isZero();
    }
}
