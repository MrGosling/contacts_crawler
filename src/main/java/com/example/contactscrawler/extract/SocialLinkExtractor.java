package com.example.contactscrawler.extract;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ссылки на соцсети/мессенджеры (crawler.extract.social-hosts). Тип SOCIAL, subtype = сеть;
 * нормализация https://host/handle без параметров (api.whatsapp.com/send?phone=N → https://wa.me/N).
 */
@Component
@Order(4)
public class SocialLinkExtractor implements ContactExtractor {

    private static final Map<String, String> NETWORKS = Map.of(
            "t.me", "telegram", "telegram.me", "telegram", "wa.me", "whatsapp", "api.whatsapp.com", "whatsapp",
            "vk.com", "vk", "ok.ru", "ok", "youtube.com", "youtube", "rutube.ru", "rutube", "dzen.ru", "dzen");
    private static final Set<String> SHARE_SEGMENTS = Set.of("share", "share.php", "sharer", "intent", "send", "");
    private static final Set<String> TWO_SEGMENT_PREFIXES = Set.of("channel", "c", "user", "group", "profile");
    private static final Pattern WA_PHONE = Pattern.compile("(?:^|&)phone=(\\d+)");

    private final Set<String> hosts;

    public SocialLinkExtractor(CrawlerProperties props) {
        this.hosts = props.getExtract().getSocialHosts().stream()
                .map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<Contact> extract(Document doc, URI pageUrl) {
        Map<String, Contact> found = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href").isEmpty() ? a.attr("href") : a.absUrl("href");
            normalize(href).ifPresent(n -> found.putIfAbsent(n[1],
                    Contact.of(ContactType.SOCIAL, n[0], href, n[1], pageUrl)));
        }
        return new ArrayList<>(found.values());
    }

    /** @return [subtype, normalizedUrl] */
    Optional<String[]> normalize(String href) {
        URI uri;
        try {
            uri = URI.create(href.trim().replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^(www|m)\\.", "");
        if (!hosts.contains(host)) {
            return Optional.empty();
        }
        String network = NETWORKS.getOrDefault(host, host.substring(0, host.indexOf('.') > 0 ? host.indexOf('.') : host.length()));
        if (host.equals("api.whatsapp.com")) {
            Matcher m = WA_PHONE.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
            return m.find() ? Optional.of(new String[]{network, "https://wa.me/" + m.group(1)}) : Optional.empty();
        }
        List<String> segments = Arrays.stream((uri.getPath() == null ? "" : uri.getPath()).split("/"))
                .filter(s -> !s.isEmpty()).toList();
        if (segments.isEmpty() || SHARE_SEGMENTS.contains(segments.get(0).toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        String handle = segments.get(0);
        if (segments.size() > 1 && TWO_SEGMENT_PREFIXES.contains(handle.toLowerCase(Locale.ROOT))) {
            handle = handle + "/" + segments.get(1);
        }
        return Optional.of(new String[]{network, "https://" + host + "/" + handle});
    }
}
