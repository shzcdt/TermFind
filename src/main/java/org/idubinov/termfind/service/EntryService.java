package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.util.TermNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EntryService {
    private EntryRepository entryRepository;

    @Autowired
    public EntryService(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    public List<Entry> findNotApprovedEntriesByTerm(String query) {

        String normalizedQuery = TermNormalizer.normalize(query);

        Term term = new Term(query, normalizedQuery);
        return entryRepository.findByTermAndApprovedFalse(term);
    }

    @Transactional
    public void approvedEntry(Entry entry){
        entryRepository.findById(entry.getId()).ifPresent(findEntry -> findEntry.setApproved(true));
    }
}