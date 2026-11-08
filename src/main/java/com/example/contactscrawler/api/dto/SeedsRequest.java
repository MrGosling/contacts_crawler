package com.example.contactscrawler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Запрос POST /api/seeds.
 */
public record SeedsRequest(@NotEmpty List<@NotBlank @Pattern(regexp = "(?i)^https?://\\S+$") String> urls) {
}
