package org.idubinov.termfind.models;

import jakarta.persistence.*;

/** Связь «книга — предмет» (книга может относиться к нескольким предметам). */
@Entity
@Table(name = "book_subjects")
@IdClass(BookSubjectId.class)
public class BookSubject {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    protected BookSubject() {
    }

    public BookSubject(Book book, Subject subject) {
        this.book = book;
        this.subject = subject;
    }

    public Book getBook() { return book; }
    public Subject getSubject() { return subject; }
}
