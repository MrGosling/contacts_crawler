package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Таблица contact; unique (domain, type, normalized_value); индексы type, domain, found_at.
 */
@Entity
@Table(name = "contact",
        uniqueConstraints = @UniqueConstraint(name = "uk_contact_key", columnNames = {"domain", "type", "normalized_value"}),
        indexes = {
                @Index(name = "idx_contact_type", columnList = "type"),
                @Index(name = "idx_contact_domain", columnList = "domain"),
                @Index(name = "idx_contact_found_at", columnList = "found_at")
        })
public class ContactEntity {

    static final int VALUE_LENGTH = 1000;
    static final int NORMALIZED_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    @Column(nullable = false, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContactType type;

    @Column(length = 64)
    private String subtype;

    @Column(name = "`value`", nullable = false, length = VALUE_LENGTH)
    private String value;

    @Column(name = "normalized_value", nullable = false, length = NORMALIZED_LENGTH)
    private String normalizedValue;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "found_at")
    private Instant foundAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "job_id", length = 36)
    private String jobId;

    protected ContactEntity() {
    }

    public static ContactEntity from(Contact c, OrganizationEntity org) {
        ContactEntity e = new ContactEntity();
        e.organization = org;
        e.domain = c.domain();
        e.type = c.type();
        e.subtype = c.subtype();
        e.value = truncate(c.value(), VALUE_LENGTH);
        e.normalizedValue = truncate(c.normalizedValue(), NORMALIZED_LENGTH);
        e.sourceUrl = truncate(c.sourceUrl(), 2048);
        e.foundAt = c.foundAt();
        e.lastSeenAt = c.lastSeenAt();
        e.jobId = c.jobId();
        return e;
    }

    public Contact toContact() {
        return new Contact(id, type, subtype, value, normalizedValue, domain, sourceUrl, foundAt, lastSeenAt, jobId);
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Обновляет lastSeenAt, если новое значение позже. */
    public void touch(Instant seen) {
        if (seen != null && (lastSeenAt == null || seen.isAfter(lastSeenAt))) {
            lastSeenAt = seen;
        }
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public ContactType getType() { return type; }
    public String getNormalizedValue() { return normalizedValue; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public OrganizationEntity getOrganization() { return organization; }
}
