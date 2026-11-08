package com.example.contactscrawler.storage;

/**
 * Хранилище контактов (crawler.storage). Методы: void saveAll(Collection<Contact>) — upsert по ContactKey (первые sourceUrl/foundAt,
 * обновление lastSeenAt), List<Contact> findAll(), List<Contact> findByFilter(ContactType type, String domain) (null = любой),
 * long count(), void deleteAll().
 */
public interface ContactRepository {
    // TODO: методы см. Javadoc
}
