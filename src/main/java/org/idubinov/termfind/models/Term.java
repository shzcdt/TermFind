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

    /** true = модерация завершена: закреплены нужные вхождения, остальные удалены, аппрувы больше недоступны. */
    @Column(name = "finalized", nullable = false)
    private boolean finalized = false;

    /** Кэш нейро-объяснения термина (генерируется по кнопке, сбрасывается при финализации). */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    /** true = крауд-верификация пройдена (порог 👍) — Level-1 мгновенный ответ. Заполняется в Фазе D. */
    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "upvotes", nullable = false)
    private int upvotes = 0;

    @Column(name = "downvotes", nullable = false)
    private int downvotes = 0;

    public Term() {
    }

    public Term(String displayForm, String normalizedForm) {
        this.displayForm = displayForm;
        this.normalizedForm = normalizedForm;
    }

    public Long getId() { return id; }
    public String getDisplayForm() { return displayForm; }
    public String getNormalizedForm() { return normalizedForm; }
    public boolean isFinalized() { return finalized; }
    public String getSummary() { return summary; }
    public boolean isVerified() { return verified; }
    public int getUpvotes() { return upvotes; }
    public int getDownvotes() { return downvotes; }
    public void setSummary(String summary) { this.summary = summary; }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public void setDownvotes(int downvotes) {
        this.downvotes = downvotes;
    }

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
