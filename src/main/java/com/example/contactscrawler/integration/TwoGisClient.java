package com.example.contactscrawler.integration;

/**
 * Feign-клиент 2GIS Places API (crawler.twogis.*). TODO: @FeignClient(name="twogis", url="${crawler.twogis.base-url}").
 * Методы: TwoGisResponse searchItems(@RequestParam q, @RequestParam("key") apiKey, fields, page, pageSize) -> GET /3.0/items.
 */
public interface TwoGisClient {
    // TODO: методы см. Javadoc
}
