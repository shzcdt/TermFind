package org.idubinov.termfind.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Кэш ответов LLM: ключ sha256(term + bookIds + promptType), TTL 7 дней. */
@Entity
@Table(name = "llm_cache")
public class LlmCache {

    @Id
    @Column(name = "key", length = 64, nullable = false)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public LlmCache() {
    }

    public LlmCache(String key, String value, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.key = key;
        this.value = value;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
