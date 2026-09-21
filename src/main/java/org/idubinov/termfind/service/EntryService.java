package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.util.EntryScorer;
import org.idubinov.termfind.util.TermNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EntryService {

    private final EntryRepository entryRepository;
    private final TermRepository termRepository;

    @Autowired
    public EntryService(EntryRepository entryRepository, TermRepository termRepository) {
        this.entryRepository = entryRepository;
        this.termRepository = termRepository;
    }

    public List<Entry> findNotApprovedEntriesByTerm(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        return entryRepository.findNotApprovedWithBookByTermNormalizedForm(normalizedQuery);
    }

    /**
     * Единая точка «что показывать пользователю»: фильтр обрывков, дедуп по тексту,
     * сортировка (approved первыми, затем по скору, затем по странице).
     * Используется ботом, REST и отчетом.
     */
    public List<Entry> presentable(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        List<Entry> all = entryRepository.findWithBookByTermNormalizedForm(normalizedQuery);

        Map<String, Entry> deduped = new LinkedHashMap<>();
        for (Entry e : all) {
            if (!EntryScorer.passesFilter(e.getText())) continue;
            deduped.putIfAbsent(EntryScorer.dedupKey(e.getText()), e);
        }

        return deduped.values().stream()
                .sorted(Comparator
                        .comparing((Entry e) -> e.isApproved())  // approved первыми
                        .thenComparing(e -> e.getType() == Entry.EntryType.DEFINITION ? 0 : 1) // определения выше упоминаний
                        .thenComparing(e -> -EntryScorer.score(e.getText(),
                                e.getType() == Entry.EntryType.DEFINITION))
                        .thenComparing(Entry::getPageNumber))
                .toList();
    }

    /** Сколько вхождений было отброшено фильтром/дедупом — для честной статистики в отчете. */
    public int filteredOutCount(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        List<Entry> all = entryRepository.findWithBookByTermNormalizedForm(normalizedQuery);
        return all.size() - presentable(query).size();
    }

    /** Аппрув отдельного вхождения. Их может быть несколько — до финализации термина. */
    @Transactional
    public boolean approveEntry(Long id) {
        return entryRepository.findById(id).map(entry -> {
            if (entry.getTerm().isFinalized()) {
                return false; // финализированный термин изменять нельзя
            }
            entry.setApproved(true);
            return true;
        }).orElse(false);
    }

    /**
     * Финализация термина: закрепляем аппрувнутые вхождения, все неподтвержденные удаляем.
     * После этого термин «запомнен» — аппрувы и индексация для него больше не выполняются.
     */
    @Transactional
    public boolean finalizeTerm(Long termId) {
        return termRepository.findById(termId).map(term -> {
            entryRepository.deleteByTermIdAndApprovedFalse(termId);
            term.setFinalized(true);
            termRepository.save(term);
            return true;
        }).orElse(false);
    }

    public Optional<Entry> findEntryWithBook(Long id) {
        return entryRepository.findWithBookById(id);
    }

    public Optional<Term> findTermByQuery(String query) {
        return termRepository.findByNormalizedForm(TermNormalizer.normalize(query));
    }
}
