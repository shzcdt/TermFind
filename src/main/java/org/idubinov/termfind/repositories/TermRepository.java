package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {

    Optional<Term> findByNormalizedForm(String normalizedForm);

    /** Термины, у которых есть вхождения в этой книге — им надо сбросить кэш синтеза. */
    @Query("select distinct e.term from Entry e where e.book.id = :bookId")
    List<Term> findTermsWithEntriesInBook(@Param("bookId") Long bookId);
}
