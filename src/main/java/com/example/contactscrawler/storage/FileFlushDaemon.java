package com.example.contactscrawler.storage;

/**
 * Демон-поток (setDaemon(true), имя storage-flusher): каждые crawler.storage-file.flush-interval вызывает FileContactRepository.flush().
 * Методы: start(), stop() (interrupt + join + финальный flush).
 */
public class FileFlushDaemon {
    // TODO: поля и методы см. Javadoc
}
