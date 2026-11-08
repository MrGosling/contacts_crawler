package com.example.contactscrawler.extract;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Название организации: schema.org name (Organization/LocalBusiness) → og:site_name → &lt;title&gt; без суффиксов
 * (« — Контакты», « | Главная»). Хранится как атрибут организации, а не как контакт.
 */
@Component
public class OrgNameExtractor {

    private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+[—–|:-]{1,2}\\s+|\\s*\\|\\s*");

    private final StructuredDataReader reader;

    public OrgNameExtractor(StructuredDataReader reader) {
        this.reader = reader;
    }

    public Optional<String> extract(Document doc) {
        for (JsonNode node : reader.jsonLd(doc)) {
            if (StructuredDataReader.isType(node, StructuredDataReader.ORGANIZATION_TYPES)) {
                JsonNode name = node.get("name");
                if (name != null && name.isTextual() && !name.asText().isBlank()) {
                    return Optional.of(name.asText().trim());
                }
            }
        }
        for (Element el : doc.select("[itemscope][itemtype*=Organization], [itemscope][itemtype*=LocalBusiness]")) {
            Element name = el.selectFirst("[itemprop=name]");
            if (name != null && !StructuredDataReader.itemValue(name).isBlank()) {
                return Optional.of(StructuredDataReader.itemValue(name).trim());
            }
        }
        Element og = doc.selectFirst("meta[property=og:site_name]");
        if (og != null && !og.attr("content").isBlank()) {
            return Optional.of(og.attr("content").trim());
        }
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            String first = TITLE_SEPARATOR.split(title.trim(), 2)[0].trim();
            return first.isEmpty() ? Optional.empty() : Optional.of(first);
        }
        return Optional.empty();
    }
}
