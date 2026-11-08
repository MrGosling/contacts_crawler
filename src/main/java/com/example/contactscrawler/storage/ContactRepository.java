package com.example.contactscrawler.storage;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Хранилище контактов (crawler.storage). saveAll — upsert по ContactKey: первые sourceUrl/foundAt сохраняются,
 * обновляется lastSeenAt. Реализации потокобезопасны.
 */
public interface ContactRepository {

    void saveAll(Collection<Contact> contacts);

    List<Contact> findAll();

    /** null = любой тип / домен. */
    List<Contact> findByFilter(ContactType type, String domain);

    long count();

    void deleteAll();

    /** Название организации по домену (первое найденное сохраняется). */
    void saveOrganization(String domain, String name, String website);

    /** domain → название организации. */
    Map<String, String> organizationNames();
}
