package com.example.contactscrawler.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Извлекает href всех &lt;a&gt; с учётом &lt;base href&gt; (Jsoup обновляет baseUri документа при разборе тега base).
 */
@Component
public class LinkExtractor {

    public List<String> extract(Document doc, URI pageUrl) {
        if (doc.baseUri() == null || doc.baseUri().isEmpty()) {
            doc.setBaseUri(pageUrl.toString());
        }
        List<String> out = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String abs = a.absUrl("href");
            out.add(abs.isEmpty() ? a.attr("href") : abs);
        }
        return out;
    }
}
