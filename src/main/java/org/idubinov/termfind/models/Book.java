package org.idubinov.termfind.models;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "total_pages")
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
