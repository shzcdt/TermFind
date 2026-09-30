package org.idubinov.termfind.models;

import jakarta.persistence.*;

@Entity
@Table(name = "entries")
public class Entry {

    public enum EntryType { DEFINITION, MENTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id")
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType type;

    @Column(nullable = false)
    private boolean approved;

    /** Уточнение от LLM-классификатора (Фаза C): DEFINITION | USAGE | NOISE. */
    @Column(name = "llm_type", length = 16)
    private String llmType;

    /** Уверенность LLM-классификатора 0..10. */
    @Column(name = "llm_score")
    private Double llmScore;

    public Entry() {
    }

    public Entry(Term term, Book book, int pageNumber, String text, EntryType type, boolean approved) {
        this.term = term;
        this.book = book;
        this.pageNumber = pageNumber;
        this.text = text;
        this.type = type;
        this.approved = approved;
    }

    public Long getId() { return id; }
    public Term getTerm() { return term; }
    public Book getBook() { return book; }
    public int getPageNumber() { return pageNumber; }
    public String getText() { return text; }
    public EntryType getType() { return type; }
    public boolean isApproved() { return approved; }
    public String getLlmType() { return llmType; }
    public Double getLlmScore() { return llmScore; }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTerm(Term term) {
        this.term = term;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setType(EntryType type) {
        this.type = type;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public void setLlmType(String llmType) {
        this.llmType = llmType;
    }

    public void setLlmScore(Double llmScore) {
        this.llmScore = llmScore;
    }
}
