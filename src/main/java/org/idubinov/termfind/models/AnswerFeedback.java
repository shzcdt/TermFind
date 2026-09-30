package org.idubinov.termfind.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Оценка пользователем конкретного LLM-ответа (объяснение / проще / строже). */
@Entity
@Table(name = "answer_feedback", uniqueConstraints =
        @UniqueConstraint(name = "uq_answer_feedback", columnNames = {"user_telegram_id", "term_id", "kind"}))
public class AnswerFeedback {

    public enum Kind {EXPLAIN, SIMPLER, STRICTER}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_telegram_id", nullable = false)
    private long userTelegramId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id")
    private Term term;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "is_helpful", nullable = false)
    private boolean helpful;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AnswerFeedback() {
    }

    public AnswerFeedback(long userTelegramId, Term term, Kind kind, boolean helpful) {
        this.userTelegramId = userTelegramId;
        this.term = term;
        this.kind = kind;
        this.helpful = helpful;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public long getUserTelegramId() { return userTelegramId; }
    public Term getTerm() { return term; }
    public Kind getKind() { return kind; }
    public boolean isHelpful() { return helpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setHelpful(boolean helpful) {
        this.helpful = helpful;
    }
}
