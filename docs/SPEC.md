# SPEC — TermFind («ФизТех-Термины»): итоговое ТЗ реализации

**Версия:** 2.0 (интеграция черновика 1.0 с реальным состоянием кода)
**Дата:** 2026-09-29
**Статус:** утверждено к поэтапной реализации

> **Формат работы.** Этот файл — единственный источник правды по плану разработки.
> Реализация идёт фазами (A–E), каждая фаза начинается только после явного «go».
> После завершения фазы: чек-листы здесь отмечаются, пользователю отправляется
> краткое саммари (что сделано, как работает, кейс использования).
> Файл написан так, чтобы его мог прочитать и проверить сам разработчик.

---

## 1. Целевая картина (что строим)

Telegram-бот для студентов ФизТеха: пишешь термин — получаешь определение из
профильных учебников с точными ссылками на страницы, контекст использования,
рекомендованные параграфы, нейро-объяснение трёх уровней строгости и Word-файл
с полной выжимкой. Студенты могут загружать новые PDF-учебники по предметам;
качество контента поддерживается крауд-голосованием и модерацией админа.

### Кейс использования (целевой)

1. **Студент:** пишет «квазиимпульс» → карточка: синтезированное определение
   (LLM по фрагментам из книг), формула, источники с страницами, разделы
   «где используется» (из оглавления), кнопки
   `[📄 Word] [💡 Проще] [🔬 Строже] [👍 Верное] [👎 Не то]`.
2. **Студент:** жмёт 📄 Word → .docx: определения «как в учебнике», картинки
   страниц со схемами + описания, разделы, источники.
3. **Студент:** жмёт 💡 Проще → то же определение языком первокурсника.
   🔬 Строже → формально, с формулами.
4. **Студент:** загружает новый PDF (/upload → предмет → файл) → админ одобряет
   → книга парсится в фоне: тексты страниц, определения, оглавление, картинки.
5. **Студенты:** голосуют 👍/👎 под определением; 3👍 — термин верифицирован
   (мгновенный ответ без LLM), 3👎 — уходит админу на проверку.
6. **Админ:** /pending — очередь заявок на книги и спорных терминов.

---

## 2. Текущее состояние кода (отправная точка)

Проект: `~/IdeaProjects/TermFind`, Spring Boot **4.1.0**, Java, Gradle Kotlin DSL,
PostgreSQL (`ddl-auto=update`), telegrambots 6.9.0 (long polling, без стартера),
PDFBox 3.0.8, snowball-stemmer, DeepSeek (OpenAI-совместимый клиент).

| Компонент | Файл | Что делает |
|---|---|---|
| Бот | `bot/TermFindBot.java` | текст → карточка + TXT-файл; callback `explain:` `page:` `approve:` `finalize:`; polling после ApplicationReadyEvent |
| Конфиг | `bot/BotConfig.java` | `bot.username/token/admin-id` из properties (**токен лежит в git — баг**) |
| Форматтер | `bot/BotMessageFormatter.java` | HTML-карточка, клавиатуры модерации/просмотра |
| Отчёт | `bot/ReportExporter.java` | TXT со всеми вхождениями |
| Поиск | `service/SearchService.java` | ленивая индексация: новый термин → **синхронный** парсинг ВСЕХ PDF в потоке апдейта |
| Выдача | `service/EntryService.java` | presentable() (фильтр+дедуп+сортировка), approve, finalize |
| LLM | `ai/LlmClient.java` | text-only `/chat/completions`, enabled по наличию ключа |
| RAG | `ai/SummaryService.java` | explain() по presentable-вхождениям, кэш в `terms.summary`, сброс при финализации |
| Промпт | `ai/TermPromptBuilder.java` | system prompt «только наши фрагменты, ссылки (Автор, стр. N)» |
| Утилиты | `util/*` | TermNormalizer (стеммер), TextCleaner, DefinitionDetector (regex «это/называется/представляет собой»), EntryScorer (скор/фильтр/дедуп), PdfTextExtractor, PdfPageRenderer (страница→PNG), **ContextExtractor (мёртвый код)** |
| REST | `controllers/*` | `GET /api/terms/search`, `GET /api/moderation/pending`, `POST /api/moderation/approve/{id}` |
| Модель | `models/*` | Book(title, pdfPath, totalPages), Term(display/normalized, **finalized, summary**), Entry(DEFINITION\|MENTION, approved) |
| Схема | `schema.sql` | **устарел**: нет `finalized`, `summary` |

**Зависимости-хвосты:** Lucene объявлена в `build.gradle.kts` и не используется.

---

## 3. Решения по расхождениям черновика с реальностью

Черновик ТЗ 1.0 писался без доступа к коду. Построчная сверка дала 10 решений —
они имеют приоритет над черновиком:

1. **Сущности не переименовываем.** `entries` из черновика (`mentions`) — та же
   сущность, что текущая `entries`. Добавляем колонки, миграций-переименований нет.
   `term_id` (FK) сохраняем вместо денормализованного `term_normalized` — целостность
   важнее удобства сырых SQL-запросов.
2. **Стек не трогаем.** Boot остаётся 4.x (в черновике 3.2), telegrambots 6.9.0
   без spring-стартера (стартер несовместим с Boot 4 — проверено при инициализации).
3. **Lucene убираем** из `build.gradle.kts`. Поиск = PostgreSQL + snowball-стеммер
   (`TermNormalizer`), для пилота достаточно. Вернуться к Lucene — только при
   объективном торможении.
4. **«Фоновый парсинг упоминаний при одобрении книги» невозможен буквально** —
   термины заранее неизвестны. Решение: при одобрении в фоне сохраняем очищенный
   текст каждой страницы в новую таблицу `pages` и сразу индексируем все
   definition-кандидаты (их можно найти без знания терминов — regex).
   Ленивая индексация нового термина тогда читает БД (быстро), а не PDF.
5. **Индексация уходит из потока апдейта.** Сейчас `search()` парсит все PDF
   синхронно — для нового термина бот сначала отвечает «🔍 Ищу по книгам…»,
   индексация и синтез идут в фоне, карточка приходит следом. Параллельные
   запросы того же термина дедуплицируются (in-flight map).
6. **Картинки: рендер вместо извлечения.** Хрупкое извлечение встроенных
   картинок PDFBox (риск из таблицы самого черновика) — не основной путь.
   Основной: рендер нужных страниц через готовый `PdfPageRenderer` (страница→PNG).
   Извлечение встроенных картинок — опция с флагом источника в `images.source`.
7. **Vision на DeepSeek — с оговоркой.** V4-поколение (V4.1 Flash) заявляет
   image input по OpenAI-совместимому API (проверено поиском 2026-09), но
   текущий `LlmClient` шлёт только текст — расширяем под multimodal. В начале
   Фазы E — факт-чек тарифа; фича выключается флагом без кода-мусора.
8. **Безопасность:** `bot.token` уходит в `${BOT_TOKEN}` (env), токен отозвать
   у BotFather и выпустить новый (старый засвечен в git-истории).
9. **Крауд-верификация и админ-модерация сосуществуют.** Голоса 👍/👎 — на уровне
   термина (Term), не отдельной таблицы verified_definitions per book: это проще
   и напрямую закрывает «Level 1 — мгновенный ответ». Админские approve/finalize
   остаются как инструмент принудительной фиксации.
10. **Мелочи:** `schema.sql` синхронизируем (или помечаем как справочный дамп —
    схемой управляет Hibernate); `ContextExtractor` удаляем; TXT-отчёт остаётся
    рядом с docx (уже написан, ничего не стоит).

---

## 4. Целевая модель данных (эволюционные изменения)

Схемой управляет Hibernate (`ddl-auto=update`) — ниже целевое состояние,
изменения аддитивные. Названия колонок — snake_case (конвенция проекта).

```sql
-- НОВЫЕ ТАБЛИЦЫ ------------------------------------------------------------

-- Пользователи бота (авто-регистрация по telegram_id при любом апдейте)
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    telegram_id  BIGINT UNIQUE NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Предметы (сид: 8 штук из FR-6)
CREATE TABLE subjects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    approved    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE book_subjects (
    book_id    BIGINT NOT NULL REFERENCES books(id),
    subject_id BIGINT NOT NULL REFERENCES subjects(id),
    PRIMARY KEY (book_id, subject_id)
);

-- Очищенный текст страниц (сердце Фазы B; см. решение №4)
CREATE TABLE pages (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL REFERENCES books(id),
    page_number  INTEGER NOT NULL,
    cleaned_text TEXT NOT NULL,
    UNIQUE (book_id, page_number)
);

-- Картинки: рендеры страниц и/или извлечённые изображения
CREATE TABLE images (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT NOT NULL REFERENCES books(id),
    page_number INTEGER NOT NULL,
    image_path  VARCHAR(1024) NOT NULL,
    source      VARCHAR(16) NOT NULL DEFAULT 'PAGE_RENDER', -- PAGE_RENDER | EXTRACTED
    description TEXT                                        -- заполнит Vision (Фаза E)
);

-- Заявки на загрузку книг (до одобрения админом)
CREATE TABLE book_requests (
    id               BIGSERIAL PRIMARY KEY,
    user_telegram_id BIGINT NOT NULL,
    subject_id       BIGINT NOT NULL REFERENCES subjects(id),
    file_hash        VARCHAR(64) NOT NULL,
    pdf_path         VARCHAR(1024) NOT NULL,
    title            VARCHAR(255),
    author           VARCHAR(255),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING|APPROVED|REJECTED
    admin_comment    TEXT,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- Голоса пользователей (1 голос на пользователя на термин)
CREATE TABLE feedback (
    id               BIGSERIAL PRIMARY KEY,
    user_telegram_id BIGINT NOT NULL,
    term_id          BIGINT NOT NULL REFERENCES terms(id),
    is_helpful       BOOLEAN NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_telegram_id, term_id)
);

-- Кэш LLM-ответов (классификация/синтез/проще/строже)
-- префикс llm_ чтобы не путать с общим словом
CREATE TABLE llm_cache (
    key        VARCHAR(64) PRIMARY KEY,   -- sha256(term + sorted(bookIds) + promptType)
    value      TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- ИЗМЕНЕНИЯ СУЩЕСТВУЮЩИХ ----------------------------------------------------

ALTER TABLE books   ADD COLUMN file_hash   VARCHAR(64) UNIQUE,
                    ADD COLUMN author      VARCHAR(255),
                    ADD COLUMN year        INTEGER,
                    ADD COLUMN toc         JSONB,      -- [{title, start_page, children:[…]}]
                    ADD COLUMN uploaded_by BIGINT;

ALTER TABLE terms   ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN upvotes     INTEGER NOT NULL DEFAULT 0,
                    ADD COLUMN downvotes   INTEGER NOT NULL DEFAULT 0;
-- (finalized, summary уже есть)

ALTER TABLE entries ADD COLUMN llm_type    VARCHAR(16), -- DEFINITION|USAGE|NOISE (уточнение после LLM)
                    ADD COLUMN llm_score   REAL;
```

Примечания:
- `verified_definitions` из черновика НЕ создаём как отдельную таблицу — роль
  Level-1 выполняют `terms.summary` + `terms.is_verified` (решение №9).
- Таблицу `cache` из черновика зовём `llm_cache` — яснее и не конфликтует со
  словом cache в будущем (Redis и т.п.).

---

## 5. Фазы реализации (чек-листы)

### Фаза A — Фундамент ✅ (2026-09-29)
- [x] `User` entity + repo + `UserService.getOrCreate(telegramId)` (upsert), вызов из `onUpdateReceived`
- [x] Команды `/start` (приветствие + как пользоваться), `/help`
- [x] `Subject` entity + repo + сервис; сид 8 предметов из FR-6 при старте (ApplicationRunner)
- [x] Команда `/subjects` — список предметов и число книг в каждом
- [x] `Book`: поля fileHash, author, year, toc, uploadedBy в entity
- [x] `Term`: isVerified, upvotes, downvotes в entity (колонки пригодятся фазе D)
- [x] Токен/username/adminId → `${BOT_TOKEN}` `${BOT_USERNAME}` `${BOT_ADMIN_ID}`; из properties токен удалить
  - ⚠️ Осталось вручную: ревокация старого токена у BotFather (он остался в git-истории) и задание env-переменных при запуске
- [x] Удалить `ContextExtractor`, убрать `lucene-core` из build.gradle
- [x] `schema.sql` синхронизировать с актуальной схемой (справочный дамп)
- [x] Тест: upsert пользователей (повторный апдейт не создаёт дубликат) — UserServiceTest (2 теста) + 3 теста текстов команд в BotMessageFormatterTest; всего 33 теста зелёные

### Фаза B — Конвейер книг ✅ (2026-09-30)
- [x] Диалог `/upload`: выбор предмета (inline-кнопки `picksub:`) → ожидание PDF (state в памяти бота)
- [x] Приём PDF: лимит 20 МБ (ограничение Telegram Bot API), сохранение в `uploads/`, SHA-256
- [x] Дедуп: `books.file_hash` («уже есть») и `book_requests` PENDING («уже на рассмотрении»)
- [x] Скан-детект: PDFBox, < 100 символов суммарно на первых 3 страницах → отказ «похоже на скан»
- [x] Заявка `book_requests` + уведомление админу с кнопками `[✅ Одобрить] [❌ Отклонить]` (💬 свободный комментарий отложен — reject пишет дефолт-комментарий)
- [x] Одобрение → запись в `books` (title из имени файла, fileHash, uploadedBy), связь с предметом
- [x] Фоновый extraction (@пул extractionExecutor): текст страниц → `pages`; definition-кандидаты на всех страницах → термины + `entries(DEFINITION)`; закладки PDF → `books.toc` (TocExtractor). Извлечение встроенных картинок перенесено в Фазу E (только PAGE_RENDER, решение №6 SPEC)
- [x] Отчёт админу по завершении: «📖 … N стр., M определений, оглавление: да/нет»
- [x] `SearchService` v2: индексация нового термина из `pages`; backfill — старые книги (Глинский) парсятся один раз и сохраняются в `pages`
- [x] Асинхронный поиск: новый термин → «🔍 Ищу по книгам…» → карточка по готовности; in-flight дедуп повторных запросов
- [x] Тесты: дедуп хеша, скан-детект, happy-path заявки, TOC-экстракция (38 тестов зелёных)
- Инвалидация кэша при одобрении книги → переносится в Фазу C вместе с `llm_cache`

### Фаза C — LLM-слой ✅ (2026-09-30)
- [x] `SummaryService` расширен: `classifyTerm` (батч-классификация top-20 presentable-вхождений), `variant` (💡 Проще / 🔬 Строже). Ответ LLM парсится leniently (`ClassificationParser` — мусор вокруг JSON не роняет)
- [x] Результат классификации → `entries.llm_type/llm_score` (managed-копии перечитываются по id)
- [x] Синтез-определение в карточке: если `terms.summary` есть — раздел «🧠 НЕЙРО-ОПРЕДЕЛЕНИЕ» сразу; иначе фон (после индексации: классификация → синтез → отдельное сообщение). Ведёт кэш «один синтез за раз» на термин
- [x] Кнопки `[🧠 Объяснить] [💡 Проще] [🔬 Строже]` на карточке (при включённом LLM)
- [x] `CacheService` + таблица `llm_cache`: ключ sha256(term + sorted(bookIds) + promptType), TTL 7 дней. Инвалидация: добавление книги меняет bookIds в ключе (естественное устаревание) + сброс `terms.summary` терминам с вхождениями в новой книге (в конце extractBook)
- [x] TOC-рекомендации: детерминированно без LLM — `TocMapper` разворачивает books.toc в разделы и сопоставляет страницам; в карточке «🔍 ГДЕ ИСПОЛЬЗОВАТЬ» вместо сырого списка страниц (отклонение от черновика: выбор разделов LLM заменили на маппинг — дешевле и предсказуемее; fallback на страницы сохранён)
- [x] Тесты: парсер классификации (мусор/регистр/клэмп score), стабильность и вариативность ключей кэша, TTL (45 тестов зелёных)

### Фаза D — Word и фидбек ✅ (2026-09-30)
- [x] Зависимость `poi-ooxml`; `WordService`: .docx — заголовок, определения «как в учебнике» (с источниками «книга, стр. N»), картинки страниц лучших определений (лимит 2), «Где используется» (TOC-разделы), «Источники». Word НЕ зависит от Проще/Строже (FR-13/25)
- [x] Кнопка `📄 Word` (callback `word:<termId>`) на карточке у всех: сборка в фоновом пуле, отправка SendDocument
- [x] Кнопки `👍 Верное` / `👎 Не то` (callback `vote:<termId>:up|down`): таблица `feedback` (уникальность пользователь+термин), повтор того же голоса — отмена (toggle), смена — переголосование; счётчики Term пересчитываются после каждого голоса
- [x] Правила: net ≥ 3 👍 → `terms.is_verified = TRUE` (флаг только при переходе порога); net ≤ −3 👎 → однократная эскалация — уведомление админу
- [x] `/pending` (только админ): PENDING-заявки книг с кнопками ✅/❌ + список эскалированных терминов (`TermRepository.findEscalated`)
- [x] Приветствие дополнено /upload и кнопками; кнопки карточки: [🧠 💡 🔬] + [📄 👍 👎]
- [x] Тесты: голоса (add/change/cancel), пороги 3👍/3👎 (однократность), структура docx (текст, источники, картинки; mentions-only) — 53 теста зелёных

### Фаза E — Vision и картинки
- [ ] Факт-чек DeepSeek V4.1 Flash image input (тариф, лимиты); fallback — второй OpenAI-совместимый провайдер через `llm.vision-*` свойства
- [ ] `LlmClient.completeWithImage(system, user, imageBase64)` — multimodal content
- [ ] Триггер: топ-1..2 страницы с определениями термина → рендер `PdfPageRenderer` → Vision-промпт «Опиши схему и её связь с термином X» → `images.description`
- [ ] Кэш описаний в `images.description` (привязано к странице, не к запросу)
- [ ] Вставка картинок + описаний в Word; лимит 1–2 схемы на термин
- [ ] Флаг `llm.vision.enabled=false` — фича тихо отключается (mitigation из черновика)

---

## 6. Нефункциональные требования

- Поиск по БД: < 200 мс; LLM-синтез: < 5 с (кэш — мгновенно); Word: < 10 с; extraction книги: < 2 мин на 400 стр. (фон).
- Индексация и LLM — никогда в потоке polling-апдейта (пулы `@Async` / executor'ы).
- Ключи и токены — только env; доступ к PDF — по внутренним путям.
- Идемпотентность: повторные голоса/апрувы/загрузки не создают дубликаты.
- Ошибка на одном апдейте/одной книге не роняет обработку остальных.

### Метрики успеха (замер вручную на пилоте, 100 тестовых терминов)
precision ≥ 85% · recall ≥ 70% · время ответа < 5 с · доля verified-ответов ≥ 60% · 👍/(👍+👎) ≥ 70% · покрытие терминов ≥ 90%.

---

## 7. Риски и mitigation (обновлено под реальность)

| Риск | Вероятность | Mitigation |
|---|---|---|
| DeepSeek vision недоступен на текущем тарифе | Средняя | Флаг `llm.vision.enabled`; fallback-провайдер через `llm.vision-*`; фаза E изолирована |
| Формулы в PDF — векторная графика/текст, а не картинки | Высокая | В Word кладём рендеры СТРАНИЦ (уже умеем), формулы — текстом из определений |
| LLM-классификация съедает токены на каждый термин | Средняя | Классификация только top-N после эвристик; всё в `llm_cache` |
| Параллельные запросы одного термина | Средняя | In-flight дедуп в SearchService |
| `ddl-auto=update` не удаляет/не переименовывает колонки и **не проставляет DEFAULT при добавлении NOT NULL на непустую таблицу** (поймано на Фазе A: `upvotes/downvotes/is_verified` на живой БД) | Низкая | Мы избегаем переименований (решение №1); новые NOT NULL-колонки на непустых таблицах добавлять вручную: `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …` |
| PDFBox не тянет закладки/картинки у конкретных книг | Средняя | Мягкая деградация: нет TOC → страницы; нет картинок → PAGE_RENDER |
| Модерация — бутылочное горлышко | Средняя | Для 5 студентов ок; авто-отсев: хеш + скан-детект уже в конвейере |
| Студенты грузят мусор | Средняя | Модерация админом обязательна, скан-детект, whitelist предметов |

---

## 8. Осознанно отложено (подтверждаем раздел 10 черновика)

Граф знаний (связи терминов) · голосовой ввод · веб-интерфейс · OCR для сканов ·
фингерпринт книг (MinHash) · курсы 1–4 внутри предметов · LLM-поиск «в какой книге
искать» (Level 3) · полная авто-модерация заявок · `users.level`/`preferred_books`
(кнопки Проще/Строже закрывают персонализацию дешевле).

---

## 9. История изменений ТЗ

- **2026-09-29 — Фаза A реализована:** users/subjects/book_subjects, команды /start /help /subjects, поля Book и Term под будущие фазы, секреты в env, чистка мёртвого кода (ContextExtractor, Lucene), schema.sql синхронизирован, 33 теста зелёные. Вручную за разработчиком: ревокация токена у BotFather + env-переменные запуска.
- **2026-09-29 — v2.0:** черновик 1.0 сверен с кодом; зафиксированы решения №1–10;
  добавлены таблицы `pages` (ключевое архитектурное отличие от черновика) и
  `llm_cache`; Vision переведён на «рендер страниц + факт-чек тарифа»;
  верификация упрощена до term-level; план разбит на фазы A–E.
- Черновик 1.0 (2026-09): исходная постановка, разделы 1–13.
