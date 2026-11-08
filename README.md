# Contacts Crawler

Учебный Spring Boot краулер открытых контактных данных организаций.

## Быстрый старт

### 1. Получить проект

```bash
git clone git@github.com:MrGosling/contacts_crawler.git
cd contacts_crawler
```

Нужны только Docker и Docker Compose. JDK и Maven на машине ставить не нужно.

### 2. Запустить в Docker

```bash
docker compose up -d --build
docker compose ps          # статус crawler: healthy (первый запуск — несколько минут)
```

Остановить: `docker compose stop`. Удалить контейнер, данные сохранятся: `docker compose down`.
Удалить всё, включая данные: `docker compose down -v`.

### 3. Проверить, что сервис запущен

Откройте в браузере:

- http://localhost:8080/actuator/health — должно быть `{"status":"UP"}`;
- http://localhost:8080/swagger-ui.html — документация и кнопки **Try it out**.

Или из терминала:

```bash
curl -s localhost:8080/actuator/health
```

### 4. Запустить парсер на https://www.utmn.ru/kontakty/

Через curl:

```bash
curl -s -X POST localhost:8080/api/crawl \
  -H 'Content-Type: application/json' \
  -d '{"urls":["https://www.utmn.ru/kontakty/"],"maxDepth":1,"maxPagesPerDomain":20,"sameDomainOnly":true,"delayMs":1100}'
```

Ответ `202` содержит `jobId`. Сохраните его в переменную:

```bash
JOB=$(curl -s -X POST localhost:8080/api/crawl \
  -H 'Content-Type: application/json' \
  -d '{"urls":["https://www.utmn.ru/kontakty/"],"maxDepth":1,"maxPagesPerDomain":20,"sameDomainOnly":true,"delayMs":1100}' \
  | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
echo $JOB
```

#### Через браузер (Swagger UI)

1. Откройте http://localhost:8080/swagger-ui.html.
2. Найдите группу **crawl-controller** и строку `POST /api/crawl`, нажмите на неё.
3. Нажмите **Try it out** — поле тела станет редактируемым.
4. Замените содержимое **Request body** на:

   ```json
   {
     "urls": ["https://www.utmn.ru/kontakty/"],
     "maxDepth": 1,
     "maxPagesPerDomain": 20,
     "sameDomainOnly": true,
     "delayMs": 1100
   }
   ```

5. Нажмите **Execute**. Ожидаемо: код **202**, в теле `"status": "QUEUED"` и `jobId`, в заголовке `Location` путь `/api/crawl/<jobId>`. Скопируйте `jobId`.

Значение полей:

| Поле | Значение | Пояснение |
|---|---|---|
| `urls` | `["https://www.utmn.ru/kontakty/"]` | стартовые адреса, до 100; обязательное поле |
| `maxDepth` | `1` | `0` — только эта страница, `1` — ещё ссылки с неё, максимум `5` |
| `maxPagesPerDomain` | `20` | лимит загруженных страниц на домен, от 1 до 10000 |
| `sameDomainOnly` | `true` | идти только по `utmn.ru` и поддоменам |
| `delayMs` | `1100` | пауза между запросами к одному хосту, мс; не меньше 1000 |

Остальные поля можно не указывать — тогда берутся значения по умолчанию из настроек сервиса.
Адреса обязаны начинаться с `http://` или `https://`; `delayMs` меньше 1000 даёт `400`.

Проверить статус в браузере: `http://localhost:8080/api/crawl/ВАШ_JOB_ID` — обновляйте, пока `status` не станет `DONE`.

### 5. Проверить статус задания

```bash
curl -s localhost:8080/api/crawl/$JOB
```

Или в браузере: `http://localhost:8080/api/crawl/ВАШ_JOB_ID`.

Статусы: `QUEUED → RUNNING → DONE` (или `FAILED`, `CANCELLED`). Ждите `DONE`; поля
`pagesFetched`, `pagesSkipped`, `pagesFailed`, `contactsFound` показывают ход работы.

Если статус долго `QUEUED`, другое задание ещё выполняется (одновременно идёт только одно).

### 6. Вывести результаты (answer) по этому заданию

Контакты пока не привязываются к `jobId` в API, поэтому выбираем их по домену `utmn.ru`.
Если обходов этого сайта было несколько, в выдаче будут контакты всех них (повторы не создаются).

#### Через браузер

Откройте адреса ниже в новой вкладке. Браузер показывает JSON в виде дерева; для читаемости включите
опцию **Pretty-print** (Chrome/Edge: «Показать исходный код»/«Pretty print», Firefox: «Исходные данные» → **Pretty Print**).

| Что нужно | Адрес |
|---|---|
| Все контакты домена | http://localhost:8080/answer?domain=utmn.ru&size=200 |
| Только телефоны | http://localhost:8080/answer?domain=utmn.ru&type=PHONE&size=200 |
| Только email | http://localhost:8080/answer?domain=utmn.ru&type=EMAIL&size=200 |
| Только адреса | http://localhost:8080/answer?domain=utmn.ru&type=ADDRESS&size=200 |
| Соцсети и мессенджеры | http://localhost:8080/answer?domain=utmn.ru&type=SOCIAL&size=200 |
| Сводка по организации | http://localhost:8080/answer/organizations?domain=utmn.ru |
| Количество по типам | http://localhost:8080/answer/stats |

Параметры, которые можно добавлять к `/answer` через `&`:

| Параметр | Пример | Что делает |
|---|---|---|
| `domain` | `domain=utmn.ru` | точное совпадение домена; **без** `www.` |
| `type` | `type=PHONE` | `PHONE`, `EMAIL`, `ADDRESS`, `SOCIAL`, `HOURS`, `WEBSITE` |
| `q` | `q=приёмная` | подстрока без учёта регистра в значении контакта |
| `size` | `size=50` | записей на странице, от 1 до 200 (по умолчанию 20) |
| `page` | `page=1` | номер страницы, с нуля |
| `sort` | `sort=value` | `foundAt` (по умолчанию), `type`, `value`, `domain` |
| `dir` | `dir=asc` | `asc` или `desc` (по умолчанию `desc`) |

Пример составного адреса — только email, по алфавиту, 50 штук на странице:

```
http://localhost:8080/answer?domain=utmn.ru&type=EMAIL&sort=value&dir=asc&size=50
```

Листать страницы: меняйте `page` (`page=0`, `page=1`, …). Поле `totalPages` в ответе подскажет, сколько страниц всего.

#### Через Swagger UI

1. Откройте http://localhost:8080/swagger-ui.html и найдите группу **answer-controller**.
2. Нажмите `GET /answer` → **Try it out**.
3. Заполните поля формы: `domain` = `utmn.ru`, `type` = `PHONE` (или пусто), `size` = `200`. Остальные поля можно оставить пустыми.
4. Нажмите **Execute**. Ответ появится ниже, с кодом и телом.
5. Для сводки откройте `GET /answer/organizations` и заполните `domain`; для статистики — `GET /answer/stats` и нажмите **Execute** без параметров.

Ошибки в параметрах (например, `size=0` или `type=FAX`) вернут `400` с текстом причины.

#### Что означают поля ответа

```json
{
  "content": [
    {
      "id": 1,
      "type": "PHONE",
      "value": "+7 (3452) 29-...",
      "normalizedValue": "+73452...",
      "domain": "utmn.ru",
      "sourceUrl": "https://www.utmn.ru/kontakty/",
      "foundAt": "2026-11-08T23:01:10Z"
    }
  ],
  "page": 0,
  "size": 200,
  "totalElements": 194,
  "totalPages": 1
}
```

- `value` — значение как найдено на странице; `normalizedValue` — приведённое к единому виду (для телефонов E.164, для email — нижний регистр). Дубли ищутся по нормализованному значению.
- `sourceUrl` — страница, где контакт найден впервые.
- `foundAt` — время первого обнаружения; его можно сравнить с `startedAt` и `finishedAt` задания из шага 5, чтобы отделить контакты конкретного обхода.
- `totalElements` — число записей по фильтру, а не число находок при обходе.

#### Сохранить в файл

Из браузера: откройте адрес, нажмите правой кнопкой → **Сохранить как** (Save as). Из терминала:

```bash
curl -s 'localhost:8080/answer?domain=utmn.ru&size=200' > utmn_contacts.json
```

В фильтре домен указывается без `www.`: `domain=www.utmn.ru` вернёт пустой список.

## Детали Docker-развёртывания

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

## Bruno-коллекция

`collections/contacts-crawler` — функциональные проверки всех эндпоинтов (запуск/статус/отмена задания с ожиданием
завершения, валидация → 400, seeds, `/answer` с фильтрами/сортировкой/пагинацией, организации, статистика,
`DELETE /api/data`). Окружения: `docker` (профиль `prod`, `allowDelete=false`) и `local` (профиль `dev`).
По умолчанию сервис обходит собственный Swagger UI (интернет не нужен); для реальных контактов задайте `seedUrl`
и `domain` в окружении.

```bash
cd collections/contacts-crawler
npx @usebruno/cli run --env docker          # или: Bruno GUI → Open Collection → Run
# без Node.js: docker run --rm --network host -v "$PWD:/c" -w /c node:20 npx -y @usebruno/cli run --env docker
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
