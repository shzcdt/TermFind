-- Схема БД «ФизТех-Термины» для PostgreSQL.
-- Ручной путь создания БД (Hibernate при hbm2ddl.auto=update делает то же самое сам):
--   createdb termfind
--   psql -d termfind -f schema.sql

CREATE TABLE IF NOT EXISTS books (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    pdf_path     VARCHAR(1024),
    total_pages  INTEGER
);

CREATE TABLE IF NOT EXISTS terms (
    id               BIGSERIAL PRIMARY KEY,
    display_form     VARCHAR(255) NOT NULL,
    normalized_form  VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS entries (
    id           BIGSERIAL PRIMARY KEY,
    term_id      BIGINT NOT NULL REFERENCES terms (id),
    book_id      BIGINT NOT NULL REFERENCES books (id),
    page_number  INTEGER NOT NULL,
    text         VARCHAR(4000) NOT NULL,
    entry_type   VARCHAR(16) NOT NULL CHECK (entry_type IN ('DEFINITION', 'MENTION')),
    approved     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_terms_normalized ON terms (normalized_form);
CREATE INDEX IF NOT EXISTS idx_entries_term ON entries (term_id);
CREATE INDEX IF NOT EXISTS idx_entries_book_page ON entries (book_id, page_number);
