package com.example.contactscrawler.storage;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryTestData {

    private RepositoryTestData() {
    }

    public static Contact contact(int i, Instant seen) {
        ContactType type = ContactType.values()[i % ContactType.values().length];
        return new Contact(null, type, null, "Value " + i, "value-" + i, "d" + (i % 5) + ".ru",
                "https://d" + (i % 5) + ".ru/p" + i, seen, seen, "job");
    }

    public static List<Contact> contacts(int n, Instant seen) {
        List<Contact> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(contact(i, seen));
        }
        return out;
    }
}
