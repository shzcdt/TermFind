package org.idubinov.termfind.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Голос пользователя за термин: один голос на пару (пользователь, термин). */
@Entity
@Table(name = "feedback", uniqueConstraints =
        @UniqueConstraint(name = "uq_feedback_user_term", columnNames = {"user_telegram_id", "term_id"}))
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_telegram_id", nullable = false)
    private long userTelegramId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id")
    private Term term;

    /** true = 👍 Верное, false = 👎 Не то. */
    @Column(name = "is_helpful", nullable = false)
    private boolean helpful;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Feedback() {
    }

    public Feedback(long userTelegramId, Term term, boolean helpful) {
        this.userTelegramId = userTelegramId;
        this.term = term;
        this.helpful = helpful;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public long getUserTelegramId() { return userTelegramId; }
    public Term getTerm() { return term; }
    public boolean isHelpful() { return helpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setHelpful(boolean helpful) {
        this.helpful = helpful;
    }
}
