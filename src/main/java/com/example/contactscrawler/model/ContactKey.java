package com.example.contactscrawler.model;

/**
 * Ключ уникальности (domain, type, normalizedValue).
 */
public record ContactKey(String domain, ContactType type, String normalizedValue) {
}
