package com.example.contactscrawler.config;

/**
 * RejectedExecutionHandler: логирует отказ (WARN + счётчик) и выполняет задачу в вызывающем потоке (CallerRuns),
 * если пул не остановлен. Методы: rejectedExecution(Runnable r, ThreadPoolExecutor executor), long rejectedCount().
 */
public class LoggingCallerRunsPolicy {
    // TODO: поля и методы см. Javadoc
}
