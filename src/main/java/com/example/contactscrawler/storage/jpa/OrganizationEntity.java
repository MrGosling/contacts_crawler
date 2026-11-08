package com.example.contactscrawler.storage.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Таблица organization: id, domain (unique), name, website.
 */
@Entity
@Table(name = "organization")
public class OrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String domain;

    @Column(length = 500)
    private String name;

    @Column(length = 500)
    private String website;

    protected OrganizationEntity() {
    }

    public OrganizationEntity(String domain, String name, String website) {
        this.domain = domain;
        this.name = name;
        this.website = website;
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}
