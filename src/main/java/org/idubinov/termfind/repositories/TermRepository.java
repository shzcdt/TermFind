package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {

    Optional<Term> findByNormalizedForm(String normalizedForm);
}
