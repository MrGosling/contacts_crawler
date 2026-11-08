package com.example.contactscrawler.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign-клиент 2GIS Places API (crawler.twogis.*): GET /3.0/items.
 */
@FeignClient(name = "twogis", url = "${crawler.twogis.base-url}")
public interface TwoGisClient {

    @GetMapping("/3.0/items")
    TwoGisResponse searchItems(@RequestParam("q") String q,
                               @RequestParam("key") String apiKey,
                               @RequestParam("fields") String fields,
                               @RequestParam("page") int page,
                               @RequestParam("page_size") int pageSize);
}
