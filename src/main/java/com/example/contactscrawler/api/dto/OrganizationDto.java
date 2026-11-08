package com.example.contactscrawler.api.dto;

import java.util.List;

/**
 * DTO организации (группировка по домену — Q-4).
 */
public record OrganizationDto(String domain, String name, String website, List<String> phones, List<String> emails,
                              List<String> addresses, List<String> socials) {
}
