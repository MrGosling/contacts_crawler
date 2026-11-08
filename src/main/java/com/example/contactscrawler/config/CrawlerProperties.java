package com.example.contactscrawler.config;

/**
 * @ConfigurationProperties(prefix = "crawler") + @Validated. Все настройки crawler.* (см. docs 08-nonfunctional.md).
 * Свойства: engine (threads|executor|forkjoin), fetcher (webclient|resttemplate|feign), storage (memory|file|h2),
 * threads, queueCapacity, maxDepth, maxPagesPerDomain, sameDomainOnly, delayMs, perHostConcurrency, maxPageSize (DataSize),
 * connectTimeout, readTimeout (Duration), maxRedirects, retries, userAgent, seeds (List<String>), seedsFile,
 * excludedExtensions, вложенные: Scheduler{mode, fixedDelay, initialDelay, cron}, Executor{queueCapacity, shutdownTimeout},
 * ForkJoin{parallelism}, StorageFile{path, flushInterval}, AsyncLog{path}, Api{allowDelete}, Extract{defaultRegion},
 * TwoGis{enabled, baseUrl, apiKey}.
 * TODO: превратить в record/POJO с вложенными классами и @EnableConfigurationProperties.
 */
public class CrawlerProperties {
    // TODO: поля и методы см. Javadoc
}
