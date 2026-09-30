package org.idubinov.termfind.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** SHA-256 содержимого файла — дедупликация загрузок. */
    @Column(name = "file_hash", unique = true)
    private String fileHash;

    @Column(name = "author")
    private String author;

    @Column(name = "year")
    private Integer year;

    /** Оглавление (закладки PDF), JSON: [{title, start_page, children:[…]}]. Заполняется в Фазе B. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "toc")
    private String toc;

    /** Telegram ID загрузившего пользователя. */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

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
    public String getFileHash() { return fileHash; }
    public String getAuthor() { return author; }
    public Integer getYear() { return year; }
    public String getToc() { return toc; }
    public Long getUploadedBy() { return uploadedBy; }

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

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public void setToc(String toc) {
        this.toc = toc;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
