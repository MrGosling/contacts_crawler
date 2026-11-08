package com.example.contactscrawler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * RejectedExecutionHandler: логирует отказ (WARN + счётчик) и выполняет задачу в вызывающем потоке (CallerRuns),
 * если пул не остановлен; для остановленного пула бросает {@link RejectedExecutionException}, чтобы вызывающий
 * код знал, что задача не выполнена.
 */
public class LoggingCallerRunsPolicy implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallerRunsPolicy.class);

    private final LongAdder rejected = new LongAdder();

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        rejected.increment();
        if (executor.isShutdown()) {
            log.warn("Task rejected: executor is shut down");
            throw new RejectedExecutionException("Executor is shut down");
        }
        log.warn("Executor queue is full (active={}, queue={}), running task in caller thread {}",
                executor.getActiveCount(), executor.getQueue().size(), Thread.currentThread().getName());
        r.run();
    }

    public long rejectedCount() {
        return rejected.sum();
    }
}
