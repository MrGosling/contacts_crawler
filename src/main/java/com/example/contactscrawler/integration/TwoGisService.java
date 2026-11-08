package com.example.contactscrawler.integration;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.UrlNormalizer;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Поиск организаций в 2GIS Places API (Feign — основной, WebClient — альтернативный вариант) и преобразование
 * в Contact. Активна при crawler.twogis.enabled=true (see Q-10: ключа нет, по умолчанию выключено).
 */
@Service
@ConditionalOnProperty(name = "crawler.twogis.enabled", havingValue = "true")
public class TwoGisService {

    static final String FIELDS = "items.contact_groups,items.address,items.rubrics";
    private static final Set<String> SOCIAL_TYPES = Set.of("telegram", "vkontakte", "whatsapp", "instagram",
            "youtube", "ok", "viber", "facebook", "twitter");

    private final TwoGisClient client;
    private final CrawlerProperties props;
    private final WebClient webClient;
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    public TwoGisService(TwoGisClient client, CrawlerProperties props) {
        this.client = client;
        this.props = props;
        this.webClient = WebClient.builder().baseUrl(props.getTwogis().getBaseUrl()).build();
    }

    public List<Contact> search(String query, String city) {
        TwoGisResponse resp = client.searchItems(fullQuery(query, city), props.getTwogis().getApiKey(), FIELDS, 1, 10);
        return toContacts(resp);
    }

    /** WebClient-вариант того же запроса. */
    public List<Contact> searchViaWebClient(String query, String city) {
        TwoGisResponse resp = webClient.get()
                .uri(b -> b.path("/3.0/items")
                        .queryParam("q", fullQuery(query, city))
                        .queryParam("key", props.getTwogis().getApiKey())
                        .queryParam("fields", FIELDS)
                        .queryParam("page", 1)
                        .queryParam("page_size", 10)
                        .build())
                .retrieve()
                .bodyToMono(TwoGisResponse.class)
                .block(props.getReadTimeout().plus(Duration.ofSeconds(2)));
        return toContacts(resp);
    }

    private static String fullQuery(String query, String city) {
        return city == null || city.isBlank() ? query : query + " " + city;
    }

    List<Contact> toContacts(TwoGisResponse resp) {
        List<Contact> out = new ArrayList<>();
        if (resp == null || resp.result() == null || resp.result().items() == null) {
            return out;
        }
        Instant now = Instant.now();
        for (TwoGisResponse.Item item : resp.result().items()) {
            String source = "https://2gis.ru/firm/" + item.id();
            String domain = "2gis.ru";
            List<TwoGisResponse.ContactInfo> infos = new ArrayList<>();
            if (item.contactGroups() != null) {
                item.contactGroups().stream().filter(g -> g.contacts() != null).forEach(g -> infos.addAll(g.contacts()));
            }
            for (TwoGisResponse.ContactInfo ci : infos) {
                if ("website".equalsIgnoreCase(ci.type()) && ci.value() != null) {
                    domain = UrlNormalizer.domainOf(URI.create(ci.text() != null && ci.text().startsWith("http")
                            ? ci.text() : "https://" + (ci.text() != null ? ci.text() : "2gis.ru")));
                }
            }
            if (item.addressName() != null) {
                out.add(contact(ContactType.ADDRESS, "2gis", item.addressName(),
                        item.addressName().toLowerCase(Locale.ROOT), domain, source, now));
            }
            for (TwoGisResponse.ContactInfo ci : infos) {
                if (ci.type() == null || ci.value() == null) {
                    continue;
                }
                String type = ci.type().toLowerCase(Locale.ROOT);
                switch (type) {
                    case "phone" -> out.add(contact(ContactType.PHONE, "2gis", ci.text() != null ? ci.text() : ci.value(),
                            normalizePhone(ci.value()), domain, source, now));
                    case "email" -> out.add(contact(ContactType.EMAIL, "2gis", ci.value(),
                            ci.value().toLowerCase(Locale.ROOT), domain, source, now));
                    case "website" -> out.add(contact(ContactType.WEBSITE, "2gis", ci.text() != null ? ci.text() : ci.value(),
                            (ci.text() != null ? ci.text() : ci.value()).toLowerCase(Locale.ROOT), domain, source, now));
                    default -> {
                        if (SOCIAL_TYPES.contains(type)) {
                            out.add(contact(ContactType.SOCIAL, type, ci.value(), ci.value(), domain, source, now));
                        }
                    }
                }
            }
        }
        return out;
    }

    private String normalizePhone(String raw) {
        try {
            return phoneUtil.format(phoneUtil.parse(raw, props.getExtract().getDefaultRegion()),
                    PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (Exception e) {
            return raw;
        }
    }

    private static Contact contact(ContactType type, String subtype, String value, String normalized, String domain,
                                   String source, Instant now) {
        return new Contact(null, type, subtype, value, normalized, domain, source, now, now, null);
    }
}
