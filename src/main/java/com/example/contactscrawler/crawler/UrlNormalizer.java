package com.example.contactscrawler.crawler;

/**
 * Нормализация URL (docs 03-crawling.md п.5): абсолютный URL, lower-case схема/хост, без порта по умолчанию, без #fragment,
 * без utm_*, gclid, yclid, fbclid, сортировка параметров, удаление завершающего '/' кроме корня.
 * Методы: Optional<URI> normalize(String href, URI base), static String domainOf(URI) (хост без www.).
 */
public class UrlNormalizer {
    // TODO: поля и методы см. Javadoc
}
