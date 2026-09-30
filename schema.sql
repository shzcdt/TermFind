-- Справочный дамп схемы «ФизТех-Термины» для PostgreSQL.
-- Фактически схемой управляет Hibernate (ddl-auto=update); файл — для ручного
-- создания БД и сверки структуры. Синхронизируется вручную при изменении модели.
--   createdb termfind
--   psql -d termfind -f schema.sql
--
-- Последняя синхронизация: Фаза A (2026-09-29). План развития — docs/SPEC.md.

CREATE TABLE IF NOT EXISTS books (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    pdf_path     VARCHAR(1024),
    total_pages  INTEGER,
    file_hash    VARCHAR(64) UNIQUE,       -- SHA-256 содержимого (дедуп загрузок)
    author       VARCHAR(255),
    year         INTEGER,
    toc          JSONB,                    -- оглавление из закладок PDF (Фаза B)
    uploaded_by  BIGINT                    -- telegram_id загрузившего
);

CREATE TABLE IF NOT EXISTS terms (
    id               BIGSERIAL PRIMARY KEY,
    display_form     VARCHAR(255) NOT NULL,
    normalized_form  VARCHAR(255) NOT NULL UNIQUE,
    finalized        BOOLEAN NOT NULL DEFAULT FALSE,  -- модерация админом завершена
    summary          TEXT,                            -- кэш нейро-объяснения
    is_verified      BOOLEAN NOT NULL DEFAULT FALSE,  -- крауд-верификация (Фаза D)
    upvotes          INTEGER NOT NULL DEFAULT 0,
    downvotes        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS entries (
    id           BIGSERIAL PRIMARY KEY,
    term_id      BIGINT NOT NULL REFERENCES terms (id),
    book_id      BIGINT NOT NULL REFERENCES books (id),
    page_number  INTEGER NOT NULL,
    text         VARCHAR(4000) NOT NULL,
    entry_type   VARCHAR(16) NOT NULL CHECK (entry_type IN ('DEFINITION', 'MENTION')),
    approved     BOOLEAN NOT NULL DEFAULT FALSE,
    llm_type     VARCHAR(16),             -- DEFINITION | USAGE | NOISE (Фаза C)
    llm_score    REAL
);

CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    telegram_id  BIGINT NOT NULL UNIQUE,
    created_at   TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL UNIQUE,
    description  TEXT,
    approved     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS book_subjects (
    book_id      BIGINT NOT NULL REFERENCES books (id),
    subject_id   BIGINT NOT NULL REFERENCES subjects (id),
    PRIMARY KEY (book_id, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_terms_normalized ON terms (normalized_form);
CREATE INDEX IF NOT EXISTS idx_entries_term ON entries (term_id);
CREATE INDEX IF NOT EXISTS idx_entries_book_page ON entries (book_id, page_number);
