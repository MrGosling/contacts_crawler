package com.example.contactscrawler.api.dto;

import com.example.contactscrawler.model.ContactType;

import java.util.Map;

/**
 * Статистика контактов.
 */
public record StatsResponse(long total, Map<ContactType, Long> byType, Map<String, Map<ContactType, Long>> byDomain) {
}
