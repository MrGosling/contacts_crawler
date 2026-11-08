package com.example.contactscrawler.storage;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactKey;
import com.example.contactscrawler.model.ContactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Этап 1 (crawler.storage=memory). ConcurrentHashMap&lt;ContactKey, Contact&gt;, атомарный upsert через compute/merge.
 */
@Repository
@ConditionalOnProperty(name = "crawler.storage", havingValue = "memory")
public class InMemoryContactRepository implements ContactRepository {

    private final ConcurrentHashMap<ContactKey, Contact> contacts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> organizations = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public void saveAll(Collection<Contact> batch) {
        for (Contact c : batch) {
            contacts.compute(c.key(), (k, old) -> old == null ? c.withId(ids.incrementAndGet()) : old.mergeWith(c));
        }
    }

    @Override
    public List<Contact> findAll() {
        return new ArrayList<>(contacts.values());
    }

    @Override
    public List<Contact> findByFilter(ContactType type, String domain) {
        return contacts.values().stream()
                .filter(c -> type == null || c.type() == type)
                .filter(c -> domain == null || c.domain().equals(domain))
                .toList();
    }

    @Override
    public long count() {
        return contacts.size();
    }

    @Override
    public void deleteAll() {
        contacts.clear();
        organizations.clear();
    }

    @Override
    public void saveOrganization(String domain, String name, String website) {
        organizations.putIfAbsent(domain, name);
    }

    @Override
    public Map<String, String> organizationNames() {
        return Map.copyOf(organizations);
    }
}
