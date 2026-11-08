package com.example.contactscrawler.logging;

/**
 * Асинхронный логгер: BlockingQueue<LogEvent> (LinkedBlockingQueue, ограниченная) + демон-поток async-logger,
 * пишет в crawler.async-log.path (logs/crawler-async.log) через BufferedWriter.
 * Методы: log(level, message) (offer, при переполнении — счётчик потерь), start() (@PostConstruct),
 * close() (@PreDestroy) — POISON, join, дренаж остатка, flush. Порядок сообщений сохраняется.
 */
public class AsyncLogger {
    // TODO: поля и методы см. Javadoc
}
