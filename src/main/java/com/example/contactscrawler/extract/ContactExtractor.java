package com.example.contactscrawler.extract;

import com.example.contactscrawler.model.Contact;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.List;

/**
 * Stateless потокобезопасный экстрактор. Приоритет источников: JSON-LD/microdata → tel:/mailto: → текстовые эвристики.
 */
public interface ContactExtractor {

    List<Contact> extract(Document doc, URI pageUrl);
}
