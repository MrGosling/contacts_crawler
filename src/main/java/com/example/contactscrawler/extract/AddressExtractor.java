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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Адреса: JSON-LD/microdata PostalAddress → &lt;address&gt; → эвристика (блок ≤ 300 символов, ≥ 2 признака).
 * normalizedValue: схлопнутые пробелы, lower-case, без хвостовой пунктуации; value — оригинал.
 */
@Component
@Order(3)
public class AddressExtractor implements ContactExtractor {

    static final int MAX_BLOCK = 300;
    private static final String[] ADDRESS_FIELDS = {"postalCode", "addressCountry", "addressRegion",
            "addressLocality", "streetAddress"};
    private static final Set<String> POSTAL_ADDRESS = Set.of("postaladdress");
    private static final List<Pattern> FEATURES = List.of(
            Pattern.compile("(?<!\\d)\\d{6}(?!\\d)"),
            Pattern.compile("(?<!\\p{L})г\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})ул\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})(пр-т|просп\\.|пр-кт)", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})пер\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})ш\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})наб\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})д\\.\\s?\\d", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})стр\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})корп\\.", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\p{L})(офис|оф\\.)", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE));

    private final StructuredDataReader reader;

    public AddressExtractor(StructuredDataReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Contact> extract(Document doc, URI pageUrl) {
        Map<String, Contact> found = new LinkedHashMap<>();
        for (JsonNode node : reader.jsonLd(doc)) {
            JsonNode address = node.get("address");
            if (address != null && address.isTextual()) {
                add(address.asText(), "schema", pageUrl, found);
            } else if (StructuredDataReader.isType(node, POSTAL_ADDRESS)) {
                List<String> parts = new ArrayList<>();
                for (String f : ADDRESS_FIELDS) {
                    JsonNode v = node.get(f);
                    if (v != null && v.isValueNode() && !v.asText().isBlank()) {
                        parts.add(v.asText().trim());
                    }
                }
                add(String.join(", ", parts), "schema", pageUrl, found);
            }
        }
        for (Element el : reader.microdata(doc, "PostalAddress")) {
            List<String> parts = new ArrayList<>();
            for (String f : ADDRESS_FIELDS) {
                Element p = el.selectFirst("[itemprop=" + f + "]");
                if (p != null && !StructuredDataReader.itemValue(p).isBlank()) {
                    parts.add(StructuredDataReader.itemValue(p).trim());
                }
            }
            add(parts.isEmpty() ? el.text() : String.join(", ", parts), "schema", pageUrl, found);
        }
        for (Element el : doc.select("address")) {
            add(el.text(), "address-tag", pageUrl, found);
        }
        if (found.isEmpty() && doc.body() != null) {
            for (Element el : doc.body().select("p, li, td, dd, div, span")) {
                if (qualifies(el) && el.children().stream().noneMatch(AddressExtractor::qualifies)) {
                    add(el.text(), "heuristic", pageUrl, found);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static boolean qualifies(Element el) {
        String text = el.text();
        return text.length() <= MAX_BLOCK && featureCount(text) >= 2;
    }

    static int featureCount(String text) {
        int n = 0;
        for (Pattern p : FEATURES) {
            if (p.matcher(text).find()) {
                n++;
            }
        }
        return n;
    }

    private static void add(String raw, String subtype, URI pageUrl, Map<String, Contact> found) {
        if (raw == null) {
            return;
        }
        String value = raw.replaceAll("\\s+", " ").trim();
        if (value.length() < 5 || value.length() > MAX_BLOCK) {
            return;
        }
        String normalized = normalize(value);
        found.putIfAbsent(normalized, Contact.of(ContactType.ADDRESS, subtype, value, normalized, pageUrl));
    }

    static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s.,;:]+$", "");
    }
}
