package com.example.contactscrawler.extract;

import com.example.contactscrawler.model.Contact;

import java.util.List;
import java.util.Optional;

/**
 * Результат извлечения со страницы.
 */
public record ExtractionResult(List<Contact> contacts, Optional<String> organizationName) {

    public static final ExtractionResult EMPTY = new ExtractionResult(List.of(), Optional.empty());

    public ExtractionResult {
        contacts = List.copyOf(contacts);
    }
}
