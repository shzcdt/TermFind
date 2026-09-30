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

    /** Термины с эскалацией: net 👎 >= 3, ещё не верифицированы и не финализированы. */
    @Query("select t from Term t where t.finalized = false and t.verified = false " +
            "and (t.upvotes - t.downvotes) <= -3")
    List<Term> findEscalated();
}
