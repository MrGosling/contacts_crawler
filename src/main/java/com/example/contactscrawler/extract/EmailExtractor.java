package com.example.contactscrawler.extract;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Email: JSON-LD/microdata email → mailto: (без ?subject) → regex по тексту с деобфускацией [at]/(at)/[dot]/« @ ».
 * Нормализация: trim + lower-case. Исключения — crawler.extract.excluded-email-patterns.
 * Публичные почтовые домены сохраняются только при признаках организации на странице (see Q-1).
 */
@Component
@Order(2)
public class EmailExtractor implements ContactExtractor {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}");
    private static final Pattern AT = Pattern.compile(
            "\\s*[\\[({]\\s*(?:at|собака)\\s*[\\])}]\\s*|\\s+(?:at|собака)\\s+(?=[\\w-]+\\s*[\\[({]?\\s*(?:dot|точка))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DOT = Pattern.compile(
            "\\s*[\\[({]\\s*(?:dot|точка)\\s*[\\])}]\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPACED_AT = Pattern.compile("([\\w.+-])\\s+@\\s+([\\w-])");
    private static final Pattern SPACED_DOT = Pattern.compile("(@[\\w.-]*[\\w-])\\s+\\.\\s+([A-Za-z]{2,24})\\b");

    private final StructuredDataReader reader;
    private final List<Pattern> excluded;
    private final Set<String> publicDomains;

    public EmailExtractor(StructuredDataReader reader, CrawlerProperties props) {
        this.reader = reader;
        this.excluded = props.getExtract().getExcludedEmailPatterns().stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE)).toList();
        this.publicDomains = props.getExtract().getPublicEmailDomains().stream()
                .map(d -> d.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<Contact> extract(Document doc, URI pageUrl) {
        Map<String, Contact> found = new LinkedHashMap<>();
        for (JsonNode node : reader.jsonLd(doc)) {
            StructuredDataReader.texts(node, "email").forEach(e -> add(e.replaceFirst("(?i)^mailto:", ""), pageUrl, found));
        }
        for (Element el : doc.select("[itemprop=email]")) {
            add(StructuredDataReader.itemValue(el).replaceFirst("(?i)^mailto:", ""), pageUrl, found);
        }
        for (Element a : doc.select("a[href^=mailto:], a[href^=MAILTO:]")) {
            String raw = a.attr("href").substring(7);
            int q = raw.indexOf('?');
            if (q >= 0) {
                raw = raw.substring(0, q);
            }
            for (String part : URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8).split("[,;]")) {
                add(part, pageUrl, found);
            }
        }
        String text = deobfuscate(doc.body() == null ? doc.text() : doc.body().text());
        Matcher m = EMAIL.matcher(text);
        while (m.find()) {
            add(m.group(), pageUrl, found);
        }
        if (found.isEmpty()) {
            return List.of();
        }
        boolean orgSigns = reader.hasOrganizationSigns(doc);
        List<Contact> out = new ArrayList<>();
        for (Contact c : found.values()) {
            String domain = c.normalizedValue().substring(c.normalizedValue().indexOf('@') + 1);
            if (orgSigns || !publicDomains.contains(domain)) { // see Q-1
                out.add(c);
            }
        }
        return out;
    }

    private void add(String raw, URI pageUrl, Map<String, Contact> found) {
        if (raw == null) {
            return;
        }
        String value = raw.trim().replaceAll("[.,;:]+$", "");
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches() || isExcluded(normalized)) {
            return;
        }
        found.putIfAbsent(normalized, Contact.of(ContactType.EMAIL, null, value, normalized, pageUrl));
    }

    boolean isExcluded(String email) {
        return excluded.stream().anyMatch(p -> p.matcher(email).matches());
    }

    static String deobfuscate(String text) {
        String t = AT.matcher(text).replaceAll("@");
        t = DOT.matcher(t).replaceAll(".");
        t = SPACED_AT.matcher(t).replaceAll("$1@$2");
        Matcher m;
        while ((m = SPACED_DOT.matcher(t)).find()) {
            t = m.replaceFirst("$1.$2");
        }
        return t;
    }
}
