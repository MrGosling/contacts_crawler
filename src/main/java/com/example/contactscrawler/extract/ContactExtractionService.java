package com.example.contactscrawler.extract;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactKey;
import com.example.contactscrawler.model.ContactType;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Агрегирует все ContactExtractor + OrgNameExtractor; дедупликация внутри страницы по ContactKey.
 * Если на странице найдены контакты, добавляется контакт WEBSITE (origin страницы).
 */
@Service
public class ContactExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ContactExtractionService.class);

    private final List<ContactExtractor> extractors;
    private final OrgNameExtractor orgNameExtractor;

    public ContactExtractionService(List<ContactExtractor> extractors, OrgNameExtractor orgNameExtractor) {
        this.extractors = List.copyOf(extractors);
        this.orgNameExtractor = orgNameExtractor;
    }

    public ExtractionResult extract(Document doc, URI pageUrl) {
        Map<ContactKey, Contact> unique = new LinkedHashMap<>();
        for (ContactExtractor extractor : extractors) {
            try {
                for (Contact c : extractor.extract(doc, pageUrl)) {
                    unique.putIfAbsent(c.key(), c);
                }
            } catch (RuntimeException e) {
                log.warn("Extractor {} failed on {}: {}", extractor.getClass().getSimpleName(), pageUrl, e.toString());
            }
        }
        if (!unique.isEmpty()) {
            String origin = websiteOf(pageUrl);
            Contact site = Contact.of(ContactType.WEBSITE, null, origin, origin.toLowerCase(Locale.ROOT), pageUrl);
            unique.putIfAbsent(site.key(), site);
        }
        Optional<String> name;
        try {
            name = orgNameExtractor.extract(doc);
        } catch (RuntimeException e) {
            name = Optional.empty();
        }
        return new ExtractionResult(new ArrayList<>(unique.values()), name);
    }

    public static String websiteOf(URI pageUrl) {
        return pageUrl.getScheme() + "://" + pageUrl.getHost() + (pageUrl.getPort() == -1 ? "" : ":" + pageUrl.getPort());
    }
}
