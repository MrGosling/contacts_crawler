package com.example.contactscrawler.extract;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * schema.org openingHours / openingHoursSpecification → тип HOURS. Эвристики не используются.
 */
@Component
@Order(5)
public class OpeningHoursExtractor implements ContactExtractor {

    private final StructuredDataReader reader;

    public OpeningHoursExtractor(StructuredDataReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Contact> extract(Document doc, URI pageUrl) {
        Map<String, Contact> found = new LinkedHashMap<>();
        for (JsonNode node : reader.jsonLd(doc)) {
            StructuredDataReader.texts(node, "openingHours").forEach(h -> add(h, pageUrl, found));
            JsonNode spec = node.get("openingHoursSpecification");
            if (spec != null) {
                for (JsonNode s : spec.isArray() ? spec : List.of(spec)) {
                    String days = String.join(",", StructuredDataReader.texts(s, "dayOfWeek").stream()
                            .map(d -> d.substring(d.lastIndexOf('/') + 1)).toList());
                    String opens = s.path("opens").asText("");
                    String closes = s.path("closes").asText("");
                    if (!opens.isEmpty() || !closes.isEmpty()) {
                        add((days + " " + opens + "-" + closes).trim(), pageUrl, found);
                    }
                }
            }
        }
        for (Element el : doc.select("[itemprop=openingHours]")) {
            add(StructuredDataReader.itemValue(el), pageUrl, found);
        }
        return new ArrayList<>(found.values());
    }

    private static void add(String raw, URI pageUrl, Map<String, Contact> found) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String value = raw.replaceAll("\\s+", " ").trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        found.putIfAbsent(normalized, Contact.of(ContactType.HOURS, null, value, normalized, pageUrl));
    }
}
