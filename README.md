# Contacts Crawler

Учебный Spring Boot краулер открытых контактных данных организаций (телефоны, email, адреса, соцсети, часы
работы) с упором на многопоточность Java. Требования — `docs/requirements/README.md`.

## Запуск в Docker (основной способ)

```bash
docker compose up -d --build
curl -X POST localhost:8080/api/crawl -H 'Content-Type: application/json' -d '{"urls":["https://example.org/contacts"]}'
curl 'localhost:8080/answer?type=PHONE&page=0&size=20'
docker compose logs -f crawler
docker compose down           # данные сохраняются в томах
docker compose down -v        # с удалением данных
```

- Профиль `prod`, порт `8080`, тома: `crawler-data:/app/data` (H2 `crawler.mv.db`, `contacts.jsonl`,
  `seeds-api.txt`), `crawler-logs:/app/logs` (`crawler-async.log`); `./seeds.txt` монтируется read-only.
- Healthcheck: `curl -fs http://localhost:8080/actuator/health` (curl установлен в runtime-образ).
- Graceful shutdown: `java` — PID 1 (exec-форма ENTRYPOINT), `server.shutdown=graceful`, `stop_grace_period: 40s`;
  текущее задание получает `CANCELLED`, очередь URL сбрасывается (Q-7), буферы сбрасываются на диск.
- Любое свойство `crawler.*` переопределяется переменной окружения (`CRAWLER_THREADS=4`,
  `CRAWLER_ENGINE=threads`, `CRAWLER_TWOGIS_API_KEY=...`) или через `.env` (см. `.env.example`).

## Сборка и тесты (Maven в Docker, JDK локально не нужен)

```bash
docker compose run --rm maven mvn -B test      # тесты (без интернета)
docker compose run --rm maven mvn -B verify    # + JaCoCo check >= 70% строк (crawler, extract, storage, query, logging)
```

Отчёт покрытия: `target/site/jacoco/index.html`.

## REST API

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/crawl` | Запуск обхода → `202` + `Location: /api/crawl/{jobId}` |
| `GET` | `/api/crawl/{jobId}` | Статус задания |
| `DELETE` | `/api/crawl/{jobId}` | Отмена → `202` (`409`, если задание уже завершено) |
| `GET` | `/api/crawl/status` | Общий статус краулера |
| `GET/POST/DELETE` | `/api/seeds` | Seed-URL (добавленные через REST хранятся в `crawler.seeds-api-file`) |
| `GET` | `/answer` | Контакты: `page`, `size` (1..200), `sort` (`foundAt/type/value/domain`), `dir`, `type`, `domain`, `q` |
| `GET` | `/answer/organizations` | Агрегат по домену |
| `GET` | `/answer/stats` | Количество по типам и доменам |
| `DELETE` | `/api/data` | Очистка хранилища (`403` при `crawler.api.allow-delete=false`, в `prod` выключено) |
| `GET` | `/swagger-ui.html`, `/actuator/health` | Документация, мониторинг |

Ошибки — RFC 7807 `ProblemDetail` (`400/403/404/409/500`).

Пример:
```bash
curl -X POST localhost:8080/api/crawl -H 'Content-Type: application/json' \
  -d '{"urls":["https://a.ru/contacts"],"maxDepth":2,"maxPagesPerDomain":100,"sameDomainOnly":true,"delayMs":1000}'
curl 'localhost:8080/answer?type=EMAIL&domain=a.ru&sort=value&dir=asc'
curl 'localhost:8080/answer/organizations?q=ромашка'
curl  localhost:8080/answer/stats
```

## Переключаемые реализации

| Свойство | Значения | По умолчанию |
|---|---|---|
| `crawler.engine` | `threads` (Thread/Runnable), `executor` (ThreadPoolExecutor + PriorityBlockingQueue), `forkjoin` (ForkJoinPool + CompletableFuture) | `executor` |
| `crawler.fetcher` | `webclient`, `resttemplate`, `feign` | `webclient` |
| `crawler.storage` | `memory`, `file` (JSONL + ReentrantReadWriteLock + демон-flusher), `h2` (Spring Data JPA) | `h2` |
| `crawler.scheduler.mode` | `executor` (ScheduledExecutorService), `spring` (`@Scheduled`), `off` | `executor` |

В YAML значение `off` нужно брать в кавычки (`mode: "off"`), иначе YAML прочитает его как `false`.

## Локальная разработка

`mvn spring-boot:run` (профиль `dev` по умолчанию: H2-консоль `/h2-console`, DEBUG). В профиле `test`:
in-memory хранилище, планировщик выключен, `delay-ms`/`min-delay-ms` = 0 (D-15).
