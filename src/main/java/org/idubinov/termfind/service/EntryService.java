package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.util.TermNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EntryService {

    private final EntryRepository entryRepository;

    @Autowired
    public EntryService(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    public List<Entry> findNotApprovedEntriesByTerm(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        return entryRepository.findNotApprovedWithBookByTermNormalizedForm(normalizedQuery);
    }

    @Transactional
    public boolean approveEntry(Long id) {
        return entryRepository.findById(id).map(entry -> {
            entry.setApproved(true);
            return true;
        }).orElse(false);
    }
}
