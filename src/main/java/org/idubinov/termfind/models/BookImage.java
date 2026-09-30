package org.idubinov.termfind.models;

import jakarta.persistence.*;

/** Картинка из книги: рендер страницы или извлечённое изображение + описание от Vision. */
@Entity
@Table(name = "images", uniqueConstraints =
        @UniqueConstraint(name = "uq_images_book_page", columnNames = {"book_id", "page_number"}))
public class BookImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    /** PAGE_RENDER | EXTRACTED. */
    @Column(nullable = false)
    private String source;

    /** Описание схемы/рисунка от vision-модели. */
    @Column(columnDefinition = "text")
    private String description;

    public BookImage() {
    }

    public BookImage(Book book, int pageNumber, String imagePath, String source) {
        this.book = book;
        this.pageNumber = pageNumber;
        this.imagePath = imagePath;
        this.source = source;
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public int getPageNumber() { return pageNumber; }
    public String getImagePath() { return imagePath; }
    public String getSource() { return source; }
    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
    }
}
