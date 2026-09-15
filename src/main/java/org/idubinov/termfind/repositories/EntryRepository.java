package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {
    List<Entry> findByTermAndApprovedFalse(Term term);
    List<Entry> findByTermNormalizedFormOrderByTypeAscPageNumberAsc(String normalizedForm);
}
