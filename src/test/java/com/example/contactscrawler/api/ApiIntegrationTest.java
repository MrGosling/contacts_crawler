package com.example.contactscrawler.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Профиль test: engine=executor, fetcher=webclient, storage=memory. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void crawlThenAnswer() throws Exception {
        String jobId = crawlSandboxAndWait();
        JsonNode job = json(mvc.perform(get("/api/crawl/" + jobId)).andReturn());
        assertThat(job.get("pagesFetched").asLong()).isEqualTo(13);
        assertThat(job.get("finishedAt").isNull()).isFalse();

        mvc.perform(get("/answer").param("type", "EMAIL").param("size", "5").param("sort", "value").param("dir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(14))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].type").value("EMAIL"))
                .andExpect(jsonPath("$.content[0].domain").value("localhost"));
        mvc.perform(get("/answer").param("q", "INFO12@"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].normalizedValue").value("info12@sandbox-org.ru"));
        mvc.perform(get("/answer/organizations").param("domain", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].domain").value("localhost"))
                .andExpect(jsonPath("$.content[0].emails.length()").value(14));
        mvc.perform(get("/answer/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byType.EMAIL").value(14))
                .andExpect(jsonPath("$.byDomain.localhost.EMAIL").value(14));
        mvc.perform(get("/api/crawl/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("executor"));
        mvc.perform(delete("/api/crawl/" + jobId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        mvc.perform(get("/answer")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void validationErrors() throws Exception {
        mvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[\"ftp://a.ru\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://a.ru\"],\"maxDepth\":9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("sort", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("dir", "sideways")).andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("type", "FAX")).andExpect(status().isBadRequest());
        mvc.perform(get("/answer").param("page", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/answer/organizations").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/crawl/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
        mvc.perform(delete("/api/crawl/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void seeds() throws Exception {
        mvc.perform(delete("/api/seeds")).andExpect(status().isNoContent());
        mvc.perform(post("/api/seeds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://seed.example.org/contacts\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("https://seed.example.org/contacts"));
        mvc.perform(get("/api/seeds")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/seeds").contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[\"bad\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/seeds")).andExpect(status().isNoContent());
        mvc.perform(get("/api/seeds")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void actuatorHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
