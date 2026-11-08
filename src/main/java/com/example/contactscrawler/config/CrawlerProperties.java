package com.example.contactscrawler.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Все настройки {@code crawler.*} (docs/requirements/08-nonfunctional.md). Каждое свойство переопределяется
 * переменной окружения ({@code CRAWLER_THREADS=4} и т. п.).
 * <p>Дополнительно к перечню из требований: {@code min-delay-ms} (нижняя граница delayMs, в test = 0 — D-15),
 * {@code retry-backoff} (база экспоненциального backoff), {@code seeds-api-file} (Q-8).
 */
@Validated
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    @Pattern(regexp = "(?i)threads|executor|forkjoin")
    private String engine = "executor";
    @Pattern(regexp = "(?i)webclient|resttemplate|feign")
    private String fetcher = "webclient";
    @Pattern(regexp = "(?i)memory|file|h2")
    private String storage = "h2";
    @Min(1) @Max(256)
    private int threads = 8;
    @Min(1)
    private int queueCapacity = 10_000;
    @Min(0) @Max(5)
    private int maxDepth = 2;
    @Min(1) @Max(10_000)
    private int maxPagesPerDomain = 100;
    private boolean sameDomainOnly = true;
    @Min(0)
    private long delayMs = 1000;
    /** Минимально допустимое значение delayMs в запросе (D-15: в профиле test = 0). */
    @Min(0)
    private long minDelayMs = 1000;
    @Min(1)
    private int perHostConcurrency = 1;
    @NotNull
    private DataSize maxPageSize = DataSize.ofMegabytes(2);
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(10);
    @Min(0) @Max(10)
    private int maxRedirects = 3;
    @Min(0) @Max(5)
    private int retries = 2;
    /** Backoff: retryBackoff * 2^attempt (1 с, 2 с по умолчанию). */
    @NotNull
    private Duration retryBackoff = Duration.ofSeconds(1);
    @NotBlank
    private String userAgent = "ContactsCrawler/1.0 (+contact@example.org)";
    private List<String> seeds = new ArrayList<>();
    private String seedsFile = "seeds.txt";
    /** Q-8: seed-URL, добавленные через REST, переживают рестарт. */
    private String seedsApiFile = "data/seeds-api.txt";
    private List<String> excludedExtensions = new ArrayList<>(List.of(
            "pdf", "jpg", "jpeg", "png", "gif", "svg", "webp", "zip", "rar", "7z", "doc", "docx",
            "xls", "xlsx", "ppt", "pptx", "mp3", "mp4", "avi", "exe"));

    @Valid
    private Scheduler scheduler = new Scheduler();
    @Valid
    private Executor executor = new Executor();
    @Valid
    private ForkJoin forkjoin = new ForkJoin();
    @Valid
    private StorageFile storageFile = new StorageFile();
    @Valid
    private AsyncLog asyncLog = new AsyncLog();
    @Valid
    private Api api = new Api();
    @Valid
    private Extract extract = new Extract();
    @Valid
    private TwoGis twogis = new TwoGis();

    public static class Scheduler {
        @Pattern(regexp = "(?i)executor|spring|off")
        private String mode = "executor";
        @NotNull
        private Duration fixedDelay = Duration.ofHours(1);
        @NotNull
        private Duration initialDelay = Duration.ofMinutes(1);
        private String cron;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Duration getFixedDelay() { return fixedDelay; }
        public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
        public Duration getInitialDelay() { return initialDelay; }
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }

    public static class Executor {
        @Min(1)
        private int queueCapacity = 1000;
        @NotNull
        private Duration shutdownTimeout = Duration.ofSeconds(20);

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public Duration getShutdownTimeout() { return shutdownTimeout; }
        public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
    }

    public static class ForkJoin {
        @Min(1) @Max(256)
        private int parallelism = 4;

        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    }

    public static class StorageFile {
        @NotBlank
        private String path = "data/contacts.jsonl";
        @NotNull
        private Duration flushInterval = Duration.ofSeconds(2);

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Duration getFlushInterval() { return flushInterval; }
        public void setFlushInterval(Duration flushInterval) { this.flushInterval = flushInterval; }
    }

    public static class AsyncLog {
        @NotBlank
        private String path = "logs/crawler-async.log";
        @Min(1)
        private int capacity = 10_000;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
    }

    public static class Api {
        private boolean allowDelete = true;

        public boolean isAllowDelete() { return allowDelete; }
        public void setAllowDelete(boolean allowDelete) { this.allowDelete = allowDelete; }
    }

    public static class Extract {
        @NotBlank
        private String defaultRegion = "RU";
        /** Регулярные выражения (без учёта регистра) для отбраковки email. */
        private List<String> excludedEmailPatterns = new ArrayList<>(List.of(
                "^[^@]+@example\\..*$", "^test@.*$", "^[^@]+@([\\w-]+\\.)*sentry[\\w.-]*$",
                "^.*\\.(png|jpe?g|gif|svg|webp)$", "^(noreply|no-reply|no_reply|donotreply)@.*$"));
        /** Публичные почтовые домены (Q-1). */
        private List<String> publicEmailDomains = new ArrayList<>(List.of(
                "gmail.com", "mail.ru", "yandex.ru", "ya.ru", "bk.ru", "inbox.ru", "list.ru",
                "rambler.ru", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com"));
        private List<String> socialHosts = new ArrayList<>(List.of(
                "t.me", "telegram.me", "wa.me", "api.whatsapp.com", "vk.com", "ok.ru",
                "youtube.com", "rutube.ru", "dzen.ru"));

        public String getDefaultRegion() { return defaultRegion; }
        public void setDefaultRegion(String defaultRegion) { this.defaultRegion = defaultRegion; }
        public List<String> getExcludedEmailPatterns() { return excludedEmailPatterns; }
        public void setExcludedEmailPatterns(List<String> v) { this.excludedEmailPatterns = v; }
        public List<String> getPublicEmailDomains() { return publicEmailDomains; }
        public void setPublicEmailDomains(List<String> v) { this.publicEmailDomains = v; }
        public List<String> getSocialHosts() { return socialHosts; }
        public void setSocialHosts(List<String> socialHosts) { this.socialHosts = socialHosts; }
    }

    public static class TwoGis {
        private boolean enabled = false;
        @NotBlank
        private String baseUrl = "https://catalog.api.2gis.com";
        private String apiKey = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String getFetcher() { return fetcher; }
    public void setFetcher(String fetcher) { this.fetcher = fetcher; }
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getMaxPagesPerDomain() { return maxPagesPerDomain; }
    public void setMaxPagesPerDomain(int maxPagesPerDomain) { this.maxPagesPerDomain = maxPagesPerDomain; }
    public boolean isSameDomainOnly() { return sameDomainOnly; }
    public void setSameDomainOnly(boolean sameDomainOnly) { this.sameDomainOnly = sameDomainOnly; }
    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    public long getMinDelayMs() { return minDelayMs; }
    public void setMinDelayMs(long minDelayMs) { this.minDelayMs = minDelayMs; }
    public int getPerHostConcurrency() { return perHostConcurrency; }
    public void setPerHostConcurrency(int perHostConcurrency) { this.perHostConcurrency = perHostConcurrency; }
    public DataSize getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(DataSize maxPageSize) { this.maxPageSize = maxPageSize; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public List<String> getSeeds() { return seeds; }
    public void setSeeds(List<String> seeds) { this.seeds = seeds; }
    public String getSeedsFile() { return seedsFile; }
    public void setSeedsFile(String seedsFile) { this.seedsFile = seedsFile; }
    public String getSeedsApiFile() { return seedsApiFile; }
    public void setSeedsApiFile(String seedsApiFile) { this.seedsApiFile = seedsApiFile; }
    public List<String> getExcludedExtensions() { return excludedExtensions; }
    public void setExcludedExtensions(List<String> excludedExtensions) { this.excludedExtensions = excludedExtensions; }
    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }
    public ForkJoin getForkjoin() { return forkjoin; }
    public void setForkjoin(ForkJoin forkjoin) { this.forkjoin = forkjoin; }
    public StorageFile getStorageFile() { return storageFile; }
    public void setStorageFile(StorageFile storageFile) { this.storageFile = storageFile; }
    public AsyncLog getAsyncLog() { return asyncLog; }
    public void setAsyncLog(AsyncLog asyncLog) { this.asyncLog = asyncLog; }
    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }
    public Extract getExtract() { return extract; }
    public void setExtract(Extract extract) { this.extract = extract; }
    public TwoGis getTwogis() { return twogis; }
    public void setTwogis(TwoGis twogis) { this.twogis = twogis; }
}
