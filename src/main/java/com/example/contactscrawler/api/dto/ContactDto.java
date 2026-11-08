package com.example.contactscrawler.api.dto;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;

import java.time.Instant;

/**
 * DTO контакта.
 */
public record ContactDto(Long id, ContactType type, String subtype, String value, String normalizedValue,
                         String domain, String sourceUrl, Instant foundAt, Instant lastSeenAt) {

    public static ContactDto from(Contact c) {
        return new ContactDto(c.id(), c.type(), c.subtype(), c.value(), c.normalizedValue(), c.domain(),
                c.sourceUrl(), c.foundAt(), c.lastSeenAt());
    }
}
