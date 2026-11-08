package com.example.contactscrawler.storage;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.model.Contact;
import com.example.contactscrawler.model.ContactKey;
import com.example.contactscrawler.model.ContactType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Этап 3 (crawler.storage=file). JSON Lines, append-only. Индекс ConcurrentHashMap&lt;ContactKey, Contact&gt;,
 * буфер новых записей ConcurrentLinkedQueue, ReentrantReadWriteLock: write — flush/deleteAll, read — чтения и
 * saveAll (конкурентные вставки в потокобезопасные структуры не должны пересекаться с усечением файла).
 * В файл пишутся только новые ключи (обновления lastSeenAt — только в памяти). Сброс — FileFlushDaemon.
 */
@Repository
@ConditionalOnProperty(name = "crawler.storage", havingValue = "file")
public class FileContactRepository implements ContactRepository {

    private static final Logger log = LoggerFactory.getLogger(FileContactRepository.class);

    private final Path path;
    private final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final ConcurrentHashMap<ContactKey, Contact> index = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Contact> buffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, String> organizations = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong ids = new AtomicLong();
    private final FileFlushDaemon daemon;

    @Autowired
    public FileContactRepository(CrawlerProperties props) {
        this(Path.of(props.getStorageFile().getPath()), props.getStorageFile().getFlushInterval());
    }

    public FileContactRepository(Path path, Duration flushInterval) {
        this.path = path;
        this.daemon = new FileFlushDaemon(this::flush, flushInterval);
    }

    @PostConstruct
    public void init() {
        load();
        daemon.start();
    }

    @PreDestroy
    public void close() {
        daemon.stop(); // включает финальный flush
    }

    /** Загрузка файла в индекс; битая последняя строка (обрыв записи) пропускается с WARN и отрезается. */
    public void load() {
        lock.writeLock().lock();
        try {
            index.clear();
            if (!Files.exists(path)) {
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            String content = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            long goodBytes = 0;
            long offset = 0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + (i < lines.length - 1 ? 1 : 0);
                offset += lineBytes;
                if (line.isBlank()) {
                    if (i < lines.length - 1) {
                        goodBytes = offset;
                    }
                    continue;
                }
                try {
                    Contact c = mapper.readValue(line, Contact.class);
                    Contact withId = c.id() == null ? c.withId(ids.incrementAndGet()) : c;
                    ids.accumulateAndGet(withId.id(), Math::max);
                    index.merge(withId.key(), withId, Contact::mergeWith);
                    goodBytes = offset;
                } catch (IOException e) {
                    log.warn("Skipping corrupted line {} in {}: {}", i + 1, path, e.getMessage());
                    if (i < lines.length - 1) {
                        goodBytes = offset; // повреждение в середине: строка пропускается, файл не трогаем
                    }
                }
            }
            if (goodBytes < bytes.length) {
                try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
                    ch.truncate(goodBytes);
                }
            }
            if (goodBytes > 0 && bytes[(int) goodBytes - 1] != '\n') {
                Files.writeString(path, "\n", StandardOpenOption.APPEND);
            }
            log.info("Loaded {} contacts from {}", index.size(), path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + path, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void saveAll(Collection<Contact> contacts) {
        lock.readLock().lock();
        try {
            for (Contact c : contacts) {
                Contact[] created = new Contact[1];
                index.compute(c.key(), (k, old) -> {
                    if (old == null) {
                        created[0] = c.withId(ids.incrementAndGet());
                        return created[0];
                    }
                    return old.mergeWith(c);
                });
                if (created[0] != null) {
                    buffer.add(created[0]);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Сброс буфера в файл под write-lock. */
    public void flush() {
        lock.writeLock().lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            int n = 0;
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                Contact c;
                while ((c = buffer.poll()) != null) {
                    w.write(mapper.writeValueAsString(c));
                    w.write('\n');
                    n++;
                }
            }
            log.debug("Flushed {} contacts to {}", n, path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot flush to " + path, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Contact> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(index.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Contact> findByFilter(ContactType type, String domain) {
        lock.readLock().lock();
        try {
            return index.values().stream()
                    .filter(c -> type == null || c.type() == type)
                    .filter(c -> domain == null || c.domain().equals(domain))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return index.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            index.clear();
            buffer.clear();
            organizations.clear();
            if (Files.exists(path)) {
                try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
                    ch.truncate(0);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot truncate " + path, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void saveOrganization(String domain, String name, String website) {
        organizations.putIfAbsent(domain, name);
    }

    @Override
    public Map<String, String> organizationNames() {
        return Map.copyOf(organizations);
    }

    public int bufferedCount() {
        return buffer.size();
    }

    FileFlushDaemon daemon() {
        return daemon;
    }
}
