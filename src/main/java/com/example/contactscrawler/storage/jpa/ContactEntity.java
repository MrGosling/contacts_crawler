package com.example.contactscrawler.storage.jpa;

/**
 * @Entity contact: id, organization (ManyToOne), domain, type, subtype, value, normalized_value, source_url, found_at, last_seen_at, job_id.
 * @Table(uniqueConstraints = (domain, type, normalized_value), indexes = type, domain, found_at).
 */
public class ContactEntity {
    // TODO: поля и методы см. Javadoc
}
