package com.example.contactscrawler.query;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;

import java.util.Comparator;
import java.util.Locale;

/**
 * Параметры /answer. Невалидные значения → IllegalArgumentException (→ 400).
 */
public record ContactQuery(int page, int size, String sort, boolean ascending, ContactType type, String domain, String q) {

    public ContactQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be in 1..200");
        }
        if (!(sort.equals("foundAt") || sort.equals("type") || sort.equals("value") || sort.equals("domain"))) {
            throw new IllegalArgumentException("sort must be one of foundAt, type, value, domain");
        }
        domain = domain == null || domain.isBlank() ? null : domain.trim().toLowerCase(Locale.ROOT);
        q = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
    }

    public static ContactQuery of(int page, int size, String sort, String dir, String type, String domain, String q) {
        boolean asc;
        String d = dir == null ? "desc" : dir.toLowerCase(Locale.ROOT);
        if (d.equals("asc")) {
            asc = true;
        } else if (d.equals("desc")) {
            asc = false;
        } else {
            throw new IllegalArgumentException("dir must be asc or desc");
        }
        return new ContactQuery(page, size, sort == null ? "foundAt" : sort, asc, parseType(type), domain, q);
    }

    public static ContactType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ContactType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public boolean matches(Contact c) {
        return q == null
                || (c.value() != null && c.value().toLowerCase(Locale.ROOT).contains(q))
                || (c.normalizedValue() != null && c.normalizedValue().toLowerCase(Locale.ROOT).contains(q));
    }

    /** Компаратор по полю + id для детерминированного порядка при равенстве. */
    public Comparator<Contact> comparator() {
        Comparator<Contact> base = switch (sort) {
            case "type" -> Comparator.comparing(Contact::type);
            case "value" -> Comparator.comparing(Contact::value, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "domain" -> Comparator.comparing(Contact::domain, Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(Contact::foundAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        base = base.thenComparing(Contact::id, Comparator.nullsFirst(Comparator.naturalOrder()));
        return ascending ? base : base.reversed();
    }
}
