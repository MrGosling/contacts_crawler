package com.example.contactscrawler.query;

import com.example.contactscrawler.api.dto.StatsResponse;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.ContactRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * /answer/stats: parallelStream + Collectors.groupingByConcurrent(type, counting()) и по domain.
 * statsSequential() — эталон для сравнения в тестах.
 */
@Service
public class StatsService {

    private final ContactRepository repository;

    public StatsService(ContactRepository repository) {
        this.repository = repository;
    }

    public StatsResponse stats() {
        List<Contact> all = repository.findAll();
        Map<ContactType, Long> byType = all.parallelStream()
                .collect(Collectors.groupingByConcurrent(Contact::type, Collectors.counting()));
        Map<String, ConcurrentMap<ContactType, Long>> byDomain = all.parallelStream()
                .collect(Collectors.groupingByConcurrent(Contact::domain,
                        Collectors.groupingByConcurrent(Contact::type, Collectors.counting())));
        return toResponse(all.size(), byType, byDomain);
    }

    public StatsResponse statsSequential() {
        List<Contact> all = repository.findAll();
        Map<ContactType, Long> byType = all.stream()
                .collect(Collectors.groupingBy(Contact::type, Collectors.counting()));
        Map<String, Map<ContactType, Long>> byDomain = all.stream()
                .collect(Collectors.groupingBy(Contact::domain,
                        Collectors.groupingBy(Contact::type, Collectors.counting())));
        return toResponse(all.size(), byType, byDomain);
    }

    /** Упорядоченные карты (EnumMap/TreeMap) для стабильного JSON и сравнения. */
    private static StatsResponse toResponse(long total, Map<ContactType, Long> byType,
                                            Map<String, ? extends Map<ContactType, Long>> byDomain) {
        Map<ContactType, Long> types = new EnumMap<>(ContactType.class);
        types.putAll(byType);
        Map<String, Map<ContactType, Long>> domains = new TreeMap<>();
        byDomain.forEach((d, m) -> {
            Map<ContactType, Long> em = new EnumMap<>(ContactType.class);
            em.putAll(m);
            domains.put(d, em);
        });
        return new StatsResponse(total, types, domains);
    }
}
