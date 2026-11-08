package com.example.contactscrawler.config;

/**
 * @Configuration. Бины WebClient (Reactor Netty: connect 5s, read 10s, followRedirect до crawler.max-redirects,
 * maxInMemorySize = crawler.max-page-size, User-Agent), RestTemplate (те же таймауты), @EnableFeignClients.
 */
public class HttpClientConfig {
    // TODO: поля и методы см. Javadoc
}
