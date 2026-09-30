package org.idubinov.termfind.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private long telegramId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(long telegramId) {
        this.telegramId = telegramId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public long getTelegramId() { return telegramId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTelegramId(long telegramId) {
        this.telegramId = telegramId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
