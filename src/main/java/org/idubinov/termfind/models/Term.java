package org.idubinov.termfind.models;

import jakarta.persistence.*;

@Entity
@Table(name = "terms")
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "display_form", nullable = false)
    private String displayForm;

    @Column(name = "normalized_form", nullable = false, unique = true)
    private String normalizedForm;

    public Term() {
    }

    public Term(String displayForm, String normalizedForm) {
        this.displayForm = displayForm;
        this.normalizedForm = normalizedForm;
    }

    public Long getId() { return id; }
    public String getDisplayForm() { return displayForm; }
    public String getNormalizedForm() { return normalizedForm; }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDisplayForm(String displayForm) {
        this.displayForm = displayForm;
    }

    public void setNormalizedForm(String normalizedForm) {
        this.normalizedForm = normalizedForm;
    }
}
