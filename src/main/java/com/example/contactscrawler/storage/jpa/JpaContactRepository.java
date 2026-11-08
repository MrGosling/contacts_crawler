package com.example.contactscrawler.storage.jpa;

/**
 * Этап 4 (crawler.storage=h2). implements ContactRepository поверх ContactJpaRepository/OrganizationJpaRepository.
 * Конкурентный upsert: при DataIntegrityViolation — перечитать и обновить last_seen_at. Запись пачками в своих транзакциях.
 */
public class JpaContactRepository {
    // TODO: поля и методы см. Javadoc
}
