package com.example.contactscrawler.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO ответа 2GIS: meta, result{total, items[{name, address_name, contact_groups[{contacts[{type, value}]}], rubrics}]}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwoGisResponse(Meta meta, Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(int code) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(int total, List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String name, @JsonProperty("address_name") String addressName,
                       @JsonProperty("contact_groups") List<ContactGroup> contactGroups, List<Rubric> rubrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactGroup(List<ContactInfo> contacts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactInfo(String type, String value, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rubric(String name) {
    }
}
