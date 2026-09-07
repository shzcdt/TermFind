package org.idubinov.termfind.db;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String pdfPath;

    private int totalPages;

    public Book() {
    }

    public Book(String title, String pdfPath, int totalPages) {
        this.title = title;
        this.pdfPath = pdfPath;
        this.totalPages = totalPages;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getPdfPath() { return pdfPath; }
    public int getTotalPages() { return totalPages; }
}
