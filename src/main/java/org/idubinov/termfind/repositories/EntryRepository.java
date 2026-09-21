package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {

    /**
     * JOIN FETCH подгружает book (и term) вместе с вхождениями — иначе после закрытия
     * транзакции обращение к e.getBook() упадет с LazyInitializationException
     * (бот, в отличие от REST-контроллера, не имеет Open Session In View).
     */
    @Query("select e from Entry e join fetch e.term join fetch e.book " +
            "where e.term.normalizedForm = :normalizedForm " +
            "order by e.type asc, e.pageNumber asc")
    List<Entry> findWithBookByTermNormalizedForm(@Param("normalizedForm") String normalizedForm);

    @Query("select e from Entry e join fetch e.term join fetch e.book " +
            "where e.term.normalizedForm = :normalizedForm and e.approved = false " +
            "order by e.type asc, e.pageNumber asc")
    List<Entry> findNotApprovedWithBookByTermNormalizedForm(@Param("normalizedForm") String normalizedForm);

    /** Финализация: удалить все неподтвержденные вхождения термина. Требует транзакции. */
    void deleteByTermIdAndApprovedFalse(Long termId);

    /** Вхождение с предзагруженными term/book — для callback-обработчиков вне веб-контекста. */
    @Query("select e from Entry e join fetch e.term join fetch e.book where e.id = :id")
    java.util.Optional<Entry> findWithBookById(@Param("id") Long id);
}
