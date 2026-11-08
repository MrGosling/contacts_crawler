package com.example.contactscrawler.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Разбор JSON-LD (&lt;script type=application/ld+json&gt;, Jackson) и microdata (itemscope/itemprop).
 */
@Component
public class StructuredDataReader {

    public static final Set<String> ORGANIZATION_TYPES = Set.of(
            "organization", "localbusiness", "corporation", "store", "restaurant", "professionalservice",
            "medicalorganization", "educationalorganization", "governmentorganization", "ngo", "autodealer",
            "foodestablishment", "homeandconstructionbusiness", "financialservice", "legalservice");

    private final ObjectMapper mapper = new ObjectMapper();

    /** Все JSON-объекты из JSON-LD (включая @graph и вложенные объекты). */
    public List<JsonNode> jsonLd(Document doc) {
        List<JsonNode> out = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collect(mapper.readTree(script.data()), out);
            } catch (Exception ignored) {
                // битый JSON-LD пропускается
            }
        }
        return out;
    }

    private static void collect(JsonNode node, List<JsonNode> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(n -> collect(n, out));
        } else if (node.isObject()) {
            out.add(node);
            node.fields().forEachRemaining(e -> {
                if (e.getValue().isContainerNode()) {
                    collect(e.getValue(), out);
                }
            });
        }
    }

    /** Элементы microdata с itemtype, содержащим itemType (например, "PostalAddress"). */
    public List<Element> microdata(Document doc, String itemType) {
        return doc.select("[itemscope][itemtype*=" + itemType + "]");
    }

    public static boolean isType(JsonNode node, Set<String> lowerCaseTypes) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (lowerCaseTypes.contains(simpleType(t.asText()))) {
                    return true;
                }
            }
            return false;
        }
        return lowerCaseTypes.contains(simpleType(type.asText()));
    }

    private static String simpleType(String t) {
        return t.substring(t.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    }

    /** Строковые значения поля (строка или массив строк). */
    public static List<String> texts(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        JsonNode v = node.get(field);
        if (v == null) {
            return out;
        }
        if (v.isArray()) {
            v.forEach(x -> {
                if (x.isValueNode()) {
                    out.add(x.asText());
                }
            });
        } else if (v.isValueNode()) {
            out.add(v.asText());
        }
        return out;
    }

    /** Значение itemprop: атрибут content, href или текст. */
    public static String itemValue(Element el) {
        if (el.hasAttr("content")) {
            return el.attr("content");
        }
        if (el.hasAttr("href")) {
            return el.attr("href");
        }
        return el.text();
    }

    /** Признаки организации на странице (Q-1): schema.org Organization/LocalBusiness или og:site_name. */
    public boolean hasOrganizationSigns(Document doc) {
        if (jsonLd(doc).stream().anyMatch(n -> isType(n, ORGANIZATION_TYPES))) {
            return true;
        }
        if (!doc.select("[itemscope][itemtype*=Organization], [itemscope][itemtype*=LocalBusiness]").isEmpty()) {
            return true;
        }
        return !doc.select("meta[property=og:site_name]").isEmpty();
    }
}
