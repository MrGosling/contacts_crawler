package com.example.contactscrawler.extract;

/**
 * implements ContactExtractor. PostalAddress из JSON-LD/microdata -> <address> -> эвристика (>=2 признака: индекс 6 цифр, г., ул., пр-т,
 * д., стр., корп., офис; блок <=300 символов). normalizedValue: схлопнутые пробелы, lower-case, без хвостовой пунктуации.
 */
public class AddressExtractor {
    // TODO: поля и методы см. Javadoc
}
