package com.example.contactscrawler.query;

import com.example.contactscrawler.api.dto.ContactDto;
import com.example.contactscrawler.api.dto.OrganizationDto;
import com.example.contactscrawler.api.dto.PageResponse;
import com.example.contactscrawler.api.dto.StatsResponse;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.InMemoryContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryServicesTest {

    private InMemoryContactRepository repo;
    private ResultsService results;
    private StatsService stats;

    @BeforeEach
    void setUp() {
        repo = new InMemoryContactRepository();
        Random rnd = new Random(42);
        List<Contact> contacts = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 3000; i++) {
            ContactType type = ContactType.values()[rnd.nextInt(ContactType.values().length)];
            String domain = "site" + rnd.nextInt(20) + ".ru";
            Instant found = base.plusSeconds(rnd.nextInt(1000)); // много равных значений
            contacts.add(new Contact(null, type, null, "Val" + rnd.nextInt(500), "val-" + i, domain,
                    "https://" + domain + "/", found, found, "j"));
        }
        repo.saveAll(contacts);
        repo.saveOrganization("site1.ru", "Ромашка", "https://site1.ru");
        results = new ResultsService(repo);
        stats = new StatsService(repo);
    }

    @ParameterizedTest
    @CsvSource({"foundAt,desc", "foundAt,asc", "type,asc", "value,desc", "domain,asc"})
    void parallelEqualsSequential(String sort, String dir) {
        for (int page = 0; page < 3; page++) {
            ContactQuery q = ContactQuery.of(page, 50, sort, dir, null, null, null);
            assertThat(results.search(q, true)).isEqualTo(results.search(q, false));
        }
    }

    @RepeatedTest(10)
    void parallelFilteredEqualsSequential() {
        ContactQuery q = ContactQuery.of(1, 20, "value", "asc", "phone", "site3.ru", "VAL1");
        PageResponse<ContactDto> parallel = results.search(q);
        assertThat(parallel).isEqualTo(results.search(q, false));
        assertThat(parallel.content()).allSatisfy(c -> {
            assertThat(c.type()).isEqualTo(ContactType.PHONE);
            assertThat(c.domain()).isEqualTo("site3.ru");
            assertThat(c.value().toLowerCase()).contains("val1");
        });
    }

    @Test
    void paginationAndSorting() {
        PageResponse<ContactDto> page = results.search(ContactQuery.of(0, 200, "foundAt", "desc", null, null, null));
        assertThat(page.totalElements()).isEqualTo(3000);
        assertThat(page.totalPages()).isEqualTo(15);
        assertThat(page.content()).hasSize(200);
        assertThat(page.content()).extracting(ContactDto::foundAt).isSortedAccordingTo((a, b) -> b.compareTo(a));
        PageResponse<ContactDto> beyond = results.search(ContactQuery.of(200, 20, "foundAt", "desc", null, null, null));
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(3000);
    }

    @Test
    void invalidQueries() {
        assertThatThrownBy(() -> ContactQuery.of(0, 0, "foundAt", "desc", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactQuery.of(0, 201, "foundAt", "desc", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactQuery.of(-1, 10, "foundAt", "desc", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactQuery.of(0, 10, "bad", "desc", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactQuery.of(0, 10, "foundAt", "up", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactQuery.of(0, 10, "foundAt", "desc", "FAX", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> results.organizations(0, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ContactQuery.of(0, 10, null, null, " ", " ", " ").type()).isNull();
    }

    @Test
    void organizationsGroupedByDomain() {
        PageResponse<OrganizationDto> orgs = results.organizations(0, 100, null, null);
        assertThat(orgs.totalElements()).isEqualTo(20);
        assertThat(orgs.content()).extracting(OrganizationDto::domain).isSorted().doesNotHaveDuplicates();
        assertThat(orgs).isEqualTo(results.organizations(0, 100, null, null, false));
        OrganizationDto site1 = results.organizations(0, 10, "SITE1.RU", null).content().get(0);
        assertThat(site1.name()).isEqualTo("Ромашка");
        assertThat(site1.phones()).isSorted().doesNotHaveDuplicates();
        assertThat(results.organizations(0, 10, null, "ромашка").content()).extracting(OrganizationDto::domain)
                .containsExactly("site1.ru");
        assertThat(results.groupByDomain(repo.findAll())).hasSize(20);
    }

    @RepeatedTest(10)
    void statsParallelEqualsSequential() {
        StatsResponse parallel = stats.stats();
        assertThat(parallel).isEqualTo(stats.statsSequential());
        assertThat(parallel.total()).isEqualTo(3000);
        assertThat(parallel.byType().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(3000);
        assertThat(parallel.byDomain()).hasSize(20);
    }
}
