package com.example.contactscrawler.api;

import com.example.contactscrawler.crawler.engine.CrawlEngine;
import com.example.contactscrawler.crawler.engine.ForkJoinCrawlEngine;
import com.example.contactscrawler.crawler.fetch.FeignFetcher;
import com.example.contactscrawler.crawler.fetch.PageFetcher;
import com.example.contactscrawler.integration.TwoGisService;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.ContactRepository;
import com.example.contactscrawler.storage.jpa.CrawlJobJpaRepository;
import com.example.contactscrawler.storage.jpa.JpaContactRepository;
import com.example.contactscrawler.storage.jpa.PageEntity;
import com.example.contactscrawler.storage.jpa.PageJpaRepository;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** storage=h2, engine=forkjoin, fetcher=feign, 2GIS (Feign + WebClient) на MockWebServer, allow-delete=false. */
@SpringBootTest(properties = {
        "crawler.storage=h2", "crawler.engine=forkjoin", "crawler.fetcher=feign",
        "crawler.api.allow-delete=false", "crawler.twogis.enabled=true", "crawler.twogis.api-key=test-key",
        "crawler.scheduler.mode=spring", "crawler.scheduler.fixed-delay=PT1H", "crawler.scheduler.initial-delay=PT1H"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class H2ForkJoinFeignIntegrationTest extends AbstractApiIntegrationTest {

    static final MockWebServer TWOGIS = new MockWebServer();

    static {
        TWOGIS.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (!"test-key".equals(request.getRequestUrl().queryParameter("key"))) {
                    return new MockResponse().setResponseCode(403);
                }
                return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                        {"meta":{"code":200},"result":{"total":1,"items":[{"id":"70000001","name":"Ромашка",
                          "address_name":"ул. Ленина, 5","rubrics":[{"name":"Кафе"}],
                          "contact_groups":[{"contacts":[
                            {"type":"phone","value":"+74951234567","text":"+7 (495) 123-45-67"},
                            {"type":"email","value":"Info@Romashka.ru"},
                            {"type":"website","value":"http://link.2gis.ru/x","text":"romashka.ru"},
                            {"type":"telegram","value":"https://t.me/romashka"},
                            {"type":"fax","value":"123"}]}]}]}}""");
            }
        });
        try {
            TWOGIS.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("crawler.twogis.base-url", () -> TWOGIS.url("/").toString().replaceAll("/$", ""));
    }

    @AfterAll
    static void stop() throws IOException {
        TWOGIS.shutdown();
    }

    @Autowired
    CrawlEngine engine;
    @Autowired
    PageFetcher fetcher;
    @Autowired
    ContactRepository repository;
    @Autowired
    PageJpaRepository pages;
    @Autowired
    CrawlJobJpaRepository jobs;
    @Autowired
    TwoGisService twoGis;

    @Test
    void crawlPersistsToH2() throws Exception {
        assertThat(engine).isInstanceOf(ForkJoinCrawlEngine.class);
        assertThat(fetcher).isInstanceOf(FeignFetcher.class);
        assertThat(repository).isInstanceOf(JpaContactRepository.class);
        String jobId = crawlSandboxAndWait();
        mvc.perform(get("/answer").param("type", "EMAIL").param("domain", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(14));
        // 13 загруженных + /private/secret, пропущенная по robots.txt
        assertThat(pages.findByJobId(jobId)).hasSize(14)
                .filteredOn(p -> p.getFetchStatus() == com.example.contactscrawler.crawler.model.FetchStatus.SKIPPED_ROBOTS)
                .extracting(PageEntity::getUrl).singleElement().asString().endsWith("/private/secret");
        assertThat(jobs.findById(jobId)).get().satisfies(j -> {
            assertThat(j.getStatus().name()).isEqualTo("DONE");
            assertThat(j.getPagesFetched()).isEqualTo(13);
        });
        mvc.perform(delete("/api/data")).andExpect(status().isForbidden());
        mvc.perform(get("/api/crawl/status")).andExpect(jsonPath("$.engine").value("forkjoin"));
    }

    @Test
    void twoGisViaFeignAndWebClient() {
        List<Contact> viaFeign = twoGis.search("кафе", "Москва");
        List<Contact> viaWebClient = twoGis.searchViaWebClient("кафе", null);
        assertThat(viaFeign).extracting(Contact::type).containsExactlyInAnyOrder(
                ContactType.ADDRESS, ContactType.PHONE, ContactType.EMAIL, ContactType.WEBSITE, ContactType.SOCIAL);
        assertThat(viaFeign).filteredOn(c -> c.type() == ContactType.PHONE)
                .extracting(Contact::normalizedValue).containsExactly("+74951234567");
        assertThat(viaFeign).allMatch(c -> c.domain().equals("romashka.ru"));
        assertThat(viaWebClient).extracting(Contact::key)
                .containsExactlyElementsOf(viaFeign.stream().map(Contact::key).toList());
    }
}
