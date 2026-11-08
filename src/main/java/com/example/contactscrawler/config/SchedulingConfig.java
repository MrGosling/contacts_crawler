package com.example.contactscrawler.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling}, активна только при crawler.scheduler.mode=spring.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "crawler.scheduler.mode", havingValue = "spring")
public class SchedulingConfig {
}
