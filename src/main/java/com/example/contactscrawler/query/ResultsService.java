package com.example.contactscrawler.query;

import com.example.contactscrawler.api.dto.ContactDto;
import com.example.contactscrawler.api.dto.OrganizationDto;
import com.example.contactscrawler.api.dto.PageResponse;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.ContactRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Выборка для /answer и /answer/organizations: ContactRepository.findByFilter(type, domain) (сужение в хранилище)
 * → parallelStream().filter(q).sorted(...).toList() → страница. Лямбды без побочных эффектов (see Q-5).
 */
@Service
public class ResultsService {

    private final ContactRepository repository;

    public ResultsService(ContactRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ContactDto> search(ContactQuery query) {
        return search(query, true);
    }

    /** parallel=false — последовательная версия для сравнения в тестах. */
    public PageResponse<ContactDto> search(ContactQuery query, boolean parallel) {
        List<Contact> source = repository.findByFilter(query.type(), query.domain());
        Stream<Contact> stream = parallel ? source.parallelStream() : source.stream();
        List<ContactDto> sorted = stream
                .filter(query::matches)
                .sorted(query.comparator())
                .map(ContactDto::from)
                .toList();
        return PageResponse.slice(sorted, query.page(), query.size());
    }

    public PageResponse<OrganizationDto> organizations(int page, int size, String domain, String q) {
        return organizations(page, size, domain, q, true);
    }

    public PageResponse<OrganizationDto> organizations(int page, int size, String domain, String q, boolean parallel) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("page must be >= 0 and size in 1..200");
        }
        String d = domain == null || domain.isBlank() ? null : domain.trim().toLowerCase(Locale.ROOT);
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<Contact> source = repository.findByFilter(null, d);
        Map<String, String> names = repository.organizationNames();
        Map<String, List<Contact>> byDomain = parallel
                ? source.parallelStream().collect(Collectors.groupingByConcurrent(Contact::domain))
                : source.stream().collect(Collectors.groupingBy(Contact::domain));
        Stream<Map.Entry<String, List<Contact>>> entries = parallel
                ? byDomain.entrySet().parallelStream() : byDomain.entrySet().stream();
        List<OrganizationDto> orgs = entries
                .map(e -> toOrganization(e.getKey(), names.get(e.getKey()), e.getValue()))
                .filter(o -> needle == null || matches(o, needle))
                .sorted(Comparator.comparing(OrganizationDto::domain))
                .toList();
        return PageResponse.slice(orgs, page, size);
    }

    static OrganizationDto toOrganization(String domain, String name, Collection<Contact> contacts) {
        List<String> websites = values(contacts, ContactType.WEBSITE, true);
        return new OrganizationDto(domain, name,
                websites.isEmpty() ? "https://" + domain : websites.get(0),
                values(contacts, ContactType.PHONE, true),
                values(contacts, ContactType.EMAIL, true),
                values(contacts, ContactType.ADDRESS, false),
                values(contacts, ContactType.SOCIAL, true));
    }

    private static List<String> values(Collection<Contact> contacts, ContactType type, boolean normalized) {
        return contacts.stream()
                .filter(c -> c.type() == type)
                .map(c -> normalized ? c.normalizedValue() : c.value())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matches(OrganizationDto o, String needle) {
        return Stream.of(Stream.of(o.domain(), o.name(), o.website()), o.phones().stream(), o.emails().stream(),
                        o.addresses().stream(), o.socials().stream())
                .flatMap(s -> s)
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle));
    }

    /** Для тестов: группировка через groupingByConcurrent. */
    ConcurrentMap<String, List<Contact>> groupByDomain(List<Contact> contacts) {
        return contacts.parallelStream().collect(Collectors.groupingByConcurrent(Contact::domain));
    }
}
