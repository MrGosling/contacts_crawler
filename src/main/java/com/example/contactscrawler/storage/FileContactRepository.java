package com.example.contactscrawler.storage;

/**
 * Этап 3 (crawler.storage=file). JSONL data/contacts.jsonl append-only. Индекс ConcurrentHashMap<ContactKey, Contact>,
 * буфер новых ConcurrentLinkedQueue<Contact>, ReentrantReadWriteLock (write — flush/deleteAll, read — чтения).
 * @PostConstruct load() — пропуск битой последней строки с WARN; @PreDestroy — финальный flush. Сброс выполняет FileFlushDaemon.
 */
public class FileContactRepository {
    // TODO: поля и методы см. Javadoc
}
