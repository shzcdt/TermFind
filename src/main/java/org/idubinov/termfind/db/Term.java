package org.idubinov.termfind.db;

import jakarta.persistence.*;

@Entity
@Table(name = "terms")
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Как термин напечатан в книге, например «тензором ранга N». */
    @Column(nullable = false)
    private String displayForm;

    /** Нормальная форма после стемминга («тенз ранг») — по ней идет поиск. */
    @Column(nullable = false, unique = true)
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
}
