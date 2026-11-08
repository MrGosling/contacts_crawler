package com.example.contactscrawler.api;

import com.example.contactscrawler.support.SandboxDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Общие помощники для @SpringBootTest + MockMvc; sandbox на MockWebServer (13 страниц). */
abstract class AbstractApiIntegrationTest {

    static final SandboxDispatcher SANDBOX = new SandboxDispatcher(3, 2);
    static final MockWebServer SITE = new MockWebServer();

    static {
        SITE.setDispatcher(SANDBOX);
        try {
            SITE.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    /** Запускает обход sandbox и ждёт DONE; возвращает jobId. */
    String crawlSandboxAndWait() throws Exception {
        String body = "{\"urls\":[\"" + SITE.url("/") + "\"],\"maxDepth\":2,\"delayMs\":0}";
        MvcResult r = mvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();
        String jobId = json(r).get("jobId").asText();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            JsonNode job = json(mvc.perform(get("/api/crawl/" + jobId)).andReturn());
            return job.get("status").asText().equals("DONE");
        });
        return jobId;
    }
}
