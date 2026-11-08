package com.example.contactscrawler.extract;

/**
 * Stateless потокобезопасный экстрактор. Методы: List<Contact> extract(Document doc, URI pageUrl).
 * Приоритет источников: JSON-LD/microdata -> tel:/mailto: -> текстовые эвристики.
 */
public interface ContactExtractor {
    // TODO: методы см. Javadoc
}
