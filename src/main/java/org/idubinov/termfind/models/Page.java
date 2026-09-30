package org.idubinov.termfind.models;

import jakarta.persistence.*;

/** Очищенный текст страницы книги: снимок парсинга, чтобы поиск не перечитывал PDF. */
@Entity
@Table(name = "pages", uniqueConstraints =
        @UniqueConstraint(name = "uq_pages_book_page", columnNames = {"book_id", "page_number"}))
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "cleaned_text", nullable = false, columnDefinition = "text")
    private String cleanedText;

    public Page() {
    }

    public Page(Book book, int pageNumber, String cleanedText) {
        this.book = book;
        this.pageNumber = pageNumber;
        this.cleanedText = cleanedText;
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public int getPageNumber() { return pageNumber; }
    public String getCleanedText() { return cleanedText; }
}
