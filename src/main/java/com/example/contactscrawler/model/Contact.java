package com.example.contactscrawler.model;

import com.example.contactscrawler.crawler.UrlNormalizer;

import java.net.URI;
import java.time.Instant;

/**
 * Доменная модель контакта (не JPA), неизменяемая. Хранятся первые sourceUrl/foundAt и lastSeenAt (D-4).
 */
public record Contact(Long id, ContactType type, String subtype, String value, String normalizedValue,
                      String domain, String sourceUrl, Instant foundAt, Instant lastSeenAt, String jobId) {

    public static Contact of(ContactType type, String subtype, String value, String normalizedValue, URI pageUrl) {
        Instant now = Instant.now();
        return new Contact(null, type, subtype, value, normalizedValue, UrlNormalizer.domainOf(pageUrl),
                pageUrl.toString(), now, now, null);
    }

    public ContactKey key() {
        return new ContactKey(domain, type, normalizedValue);
    }

    public Contact withId(Long newId) {
        return new Contact(newId, type, subtype, value, normalizedValue, domain, sourceUrl, foundAt, lastSeenAt, jobId);
    }

    public Contact withJobId(String newJobId) {
        return new Contact(id, type, subtype, value, normalizedValue, domain, sourceUrl, foundAt, lastSeenAt, newJobId);
    }

    public Contact withLastSeenAt(Instant seen) {
        return new Contact(id, type, subtype, value, normalizedValue, domain, sourceUrl, foundAt, seen, jobId);
    }

    /** Upsert: сохраняет первые sourceUrl/foundAt, lastSeenAt = максимум. */
    public Contact mergeWith(Contact newer) {
        Instant seen = newer.lastSeenAt() == null || (lastSeenAt != null && lastSeenAt.isAfter(newer.lastSeenAt()))
                ? lastSeenAt : newer.lastSeenAt();
        return withLastSeenAt(seen);
    }
}
