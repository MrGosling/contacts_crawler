package com.example.contactscrawler.storage.jpa;

import com.example.contactscrawler.model.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data репозиторий контактов.
 */
public interface ContactJpaRepository extends JpaRepository<ContactEntity, Long> {

    Optional<ContactEntity> findByDomainAndTypeAndNormalizedValue(String domain, ContactType type, String normalizedValue);

    List<ContactEntity> findByTypeAndDomain(ContactType type, String domain);

    /** Сужение выборки на уровне БД; null = любой. */
    @Query("select c from ContactEntity c where (:type is null or c.type = :type) and (:domain is null or c.domain = :domain)")
    List<ContactEntity> findByFilter(@Param("type") ContactType type, @Param("domain") String domain);
}
