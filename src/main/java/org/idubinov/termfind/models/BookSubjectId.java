package org.idubinov.termfind.models;

import java.io.Serializable;
import java.util.Objects;

/** Составной ключ book_subjects; имена полей совпадают с @Id-полями BookSubject. */
public class BookSubjectId implements Serializable {

    private Long book;
    private Long subject;

    public BookSubjectId() {
    }

    public BookSubjectId(Long book, Long subject) {
        this.book = book;
        this.subject = subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookSubjectId that)) return false;
        return Objects.equals(book, that.book) && Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(book, subject);
    }
}
