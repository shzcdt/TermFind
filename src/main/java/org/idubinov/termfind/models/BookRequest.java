package org.idubinov.termfind.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Заявка пользователя на загрузку книги — до одобрения админом в books не попадает. */
@Entity
@Table(name = "book_requests")
public class BookRequest {

    public enum Status {PENDING, APPROVED, REJECTED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_telegram_id", nullable = false)
    private long userTelegramId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "file_hash", nullable = false)
    private String fileHash;

    @Column(name = "pdf_path", nullable = false)
    private String pdfPath;

    @Column(name = "title")
    private String title;

    @Column(name = "author")
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "admin_comment")
    private String adminComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public BookRequest() {
    }

    public BookRequest(long userTelegramId, Subject subject, String fileHash, String pdfPath, String title) {
        this.userTelegramId = userTelegramId;
        this.subject = subject;
        this.fileHash = fileHash;
        this.pdfPath = pdfPath;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public long getUserTelegramId() { return userTelegramId; }
    public Subject getSubject() { return subject; }
    public String getFileHash() { return fileHash; }
    public String getPdfPath() { return pdfPath; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public Status getStatus() { return status; }
    public String getAdminComment() { return adminComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }
}
