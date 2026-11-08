package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactType;
import com.example.contactscrawler.storage.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Этап 4 (crawler.storage=h2, по умолчанию). Пачка пишется в отдельной транзакции (REQUIRES_NEW, воркер не держит
 * транзакцию во время HTTP). Конкурентный upsert: при нарушении unique-ключа пачка повторяется поштучно, а для
 * конфликтующей записи — перечитывание и обновление last_seen_at.
 */
@Repository
@ConditionalOnProperty(name = "crawler.storage", havingValue = "h2", matchIfMissing = true)
public class JpaContactRepository implements ContactRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaContactRepository.class);

    private final ContactJpaRepository contacts;
    private final OrganizationJpaRepository organizations;
    private final PageJpaRepository pages;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<String, Long> orgIds = new ConcurrentHashMap<>();

    public JpaContactRepository(ContactJpaRepository contacts, OrganizationJpaRepository organizations,
                                PageJpaRepository pages, PlatformTransactionManager txManager) {
        this.contacts = contacts;
        this.organizations = organizations;
        this.pages = pages;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void saveAll(Collection<Contact> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            tx.executeWithoutResult(s -> batch.forEach(this::upsertInTx));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.debug("Batch upsert conflict, retrying item by item: {}", e.getMostSpecificCause().getMessage());
            for (Contact c : batch) {
                upsertOne(c);
            }
        }
    }

    private void upsertOne(Contact c) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                tx.executeWithoutResult(s -> upsertInTx(c));
                return;
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                // параллельная вставка того же ключа: следующая попытка найдёт запись и обновит last_seen_at
            }
        }
        log.warn("Cannot upsert contact {}", c.key());
    }

    private void upsertInTx(Contact c) {
        String normalized = ContactEntity.truncate(c.normalizedValue(), ContactEntity.NORMALIZED_LENGTH);
        contacts.findByDomainAndTypeAndNormalizedValue(c.domain(), c.type(), normalized)
                .ifPresentOrElse(e -> e.touch(c.lastSeenAt()), () -> {
                    OrganizationEntity org = organizationRef(c.domain());
                    contacts.saveAndFlush(ContactEntity.from(c, org));
                });
    }

    private OrganizationEntity organizationRef(String domain) {
        Long id = orgIds.get(domain);
        if (id != null) {
            return organizations.getReferenceById(id);
        }
        OrganizationEntity org = organizations.findByDomain(domain).orElse(null);
        if (org == null) {
            return null; // организация создаётся saveOrganization; связь проставится для следующих контактов
        }
        orgIds.put(domain, org.getId());
        return org;
    }

    @Override
    public List<Contact> findAll() {
        return contacts.findAll().stream().map(ContactEntity::toContact).toList();
    }

    @Override
    public List<Contact> findByFilter(ContactType type, String domain) {
        return contacts.findByFilter(type, domain).stream().map(ContactEntity::toContact).toList();
    }

    @Override
    public long count() {
        return contacts.count();
    }

    @Override
    public void deleteAll() {
        tx.executeWithoutResult(s -> {
            contacts.deleteAllInBatch();
            organizations.deleteAllInBatch();
            pages.deleteAllInBatch();
        });
        orgIds.clear();
    }

    @Override
    public void saveOrganization(String domain, String name, String website) {
        if (orgIds.containsKey(domain)) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Long id = tx.execute(s -> organizations.findByDomain(domain)
                        .orElseGet(() -> organizations.saveAndFlush(new OrganizationEntity(domain, name, website)))
                        .getId());
                orgIds.put(domain, id);
                return;
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                // параллельное создание — повторное чтение
            }
        }
    }

    @Override
    public Map<String, String> organizationNames() {
        Map<String, String> out = new HashMap<>();
        organizations.findAll().forEach(o -> {
            if (o.getName() != null) {
                out.put(o.getDomain(), o.getName());
            }
        });
        return out;
    }
}
