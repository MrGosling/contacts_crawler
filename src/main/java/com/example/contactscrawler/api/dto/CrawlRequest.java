package com.example.contactscrawler.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Запрос POST /api/crawl. null → значения из CrawlerProperties. Нижняя граница delayMs —
 * crawler.min-delay-ms (1000, в профиле test — 0, D-15), проверяется в CrawlParams.of.
 */
public record CrawlRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank @Pattern(regexp = "(?i)^https?://\\S+$") String> urls,
        @Min(0) @Max(5) Integer maxDepth,
        @Min(1) @Max(10000) Integer maxPagesPerDomain,
        Boolean sameDomainOnly,
        @Min(0) Long delayMs) {
}
