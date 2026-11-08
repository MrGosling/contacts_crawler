package com.example.contactscrawler.extract;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Телефоны: JSON-LD/microdata telephone → tel: → текст (PhoneNumberUtil.findNumbers, Leniency.VALID).
 * Только isValidNumber, нормализация E.164; номера, совпадающие с реквизитами (ИНН/ОГРН/КПП/БИК/счета), отбрасываются.
 */
@Component
@Order(1)
public class PhoneExtractor implements ContactExtractor {

    private static final Pattern REQUISITES = Pattern.compile(
            "(?iu)(ИНН|ОГРНИП|ОГРН|КПП|БИК|ОКПО|ОКВЭД|р/с|к/с|расч[её]тный сч[её]т|корр?\\.? ?сч[её]т)[\\s:№.]*([\\d\\s]{6,25})");

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    private final StructuredDataReader reader;
    private final String region;

    @Autowired
    public PhoneExtractor(StructuredDataReader reader, CrawlerProperties props) {
        this(reader, props.getExtract().getDefaultRegion());
    }

    public PhoneExtractor(StructuredDataReader reader, String region) {
        this.reader = reader;
        this.region = region;
    }

    @Override
    public List<Contact> extract(Document doc, URI pageUrl) {
        Set<String> requisites = requisiteDigits(doc.text());
        Map<String, Contact> found = new LinkedHashMap<>();
        for (JsonNode node : reader.jsonLd(doc)) {
            StructuredDataReader.texts(node, "telephone").forEach(t -> add(t, "schema", pageUrl, requisites, found));
        }
        for (Element el : doc.select("[itemprop=telephone]")) {
            add(StructuredDataReader.itemValue(el).replaceFirst("(?i)^tel:", ""), "schema", pageUrl, requisites, found);
        }
        for (Element a : doc.select("a[href^=tel:], a[href^=TEL:]")) {
            String raw = URLDecoder.decode(a.attr("href").substring(4).replace("+", "%2B"), StandardCharsets.UTF_8);
            add(raw, "tel", pageUrl, requisites, found);
        }
        String text = doc.body() == null ? doc.text() : doc.body().text();
        for (PhoneNumberMatch m : util.findNumbers(text, region, PhoneNumberUtil.Leniency.VALID, Long.MAX_VALUE)) {
            addParsed(m.rawString(), m.number(), "text", pageUrl, requisites, found);
        }
        return new ArrayList<>(found.values());
    }

    private void add(String raw, String subtype, URI pageUrl, Set<String> requisites, Map<String, Contact> found) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            addParsed(raw.trim(), util.parse(raw.trim(), region), subtype, pageUrl, requisites, found);
        } catch (NumberParseException ignored) {
            // не телефон
        }
    }

    private void addParsed(String raw, Phonenumber.PhoneNumber number, String subtype, URI pageUrl,
                           Set<String> requisites, Map<String, Contact> found) {
        if (!util.isValidNumber(number)) {
            return;
        }
        String digits = raw.replaceAll("\\D", "");
        if (requisites.stream().anyMatch(r -> r.contains(digits) || digits.contains(r))) {
            return;
        }
        String e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        found.putIfAbsent(e164, Contact.of(ContactType.PHONE, null, raw, e164, pageUrl));
    }

    static Set<String> requisiteDigits(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = REQUISITES.matcher(text);
        while (m.find()) {
            String digits = m.group(2).replaceAll("\\D", "");
            if (digits.length() >= 6) {
                out.add(digits);
            }
        }
        return out;
    }
}
