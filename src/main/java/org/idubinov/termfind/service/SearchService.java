package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.util.PdfTextExtractor;
import org.idubinov.termfind.repositories.BookRepository;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.util.DefinitionDetector;
import org.idubinov.termfind.util.TextCleaner;
import org.idubinov.termfind.util.TermNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final TermRepository termRepository;
    private final EntryRepository entryRepository;
    private final BookRepository bookRepository;
    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final DefinitionDetector definitionDetector = new DefinitionDetector();

    @Autowired
    public SearchService(TermRepository termRepository,
                         EntryRepository entryRepository,
                         BookRepository bookRepository) {
        this.termRepository = termRepository;
        this.entryRepository = entryRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public List<Entry> search(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        if (normalizedQuery.isEmpty()) return List.of();

        Optional<Term> existing = termRepository.findByNormalizedForm(normalizedQuery);
        if (existing.isPresent()) {
            return entryRepository.findByTermNormalizedFormOrderByTypeAscPageNumberAsc(normalizedQuery);
        }

        Term term = termRepository.save(new Term(query.trim(), normalizedQuery));

        for (Book book : bookRepository.findAll()) {
            if (book.getPdfPath() == null) continue;
            indexTermInBook(book, term, normalizedQuery);
        }

        return entryRepository.findByTermNormalizedFormOrderByTypeAscPageNumberAsc(normalizedQuery);
    }

    private void indexTermInBook(Book book, Term term, String normalizedQuery) {
        try {
            PdfTextExtractor.BookText bookText = pdfTextExtractor.extract(new File(book.getPdfPath()));

            for (PdfTextExtractor.PageText page : bookText.pages()) {
                if (page.isEmpty()) continue;

                for (var candidate : definitionDetector.detect(page.text(), page.pageNumber())) {
                    if (TermNormalizer.containsNormalized(candidate.term(), normalizedQuery)) {
                        entryRepository.save(new Entry(term, book, page.pageNumber(),
                                candidate.definition(), Entry.EntryType.DEFINITION, false));
                    }
                }

                String[] sentences = TextCleaner.sentences(TextCleaner.clean(page.text()));
                for (String sentence : sentences) {
                    if (TermNormalizer.containsNormalized(sentence, normalizedQuery)) {
                        entryRepository.save(new Entry(term, book, page.pageNumber(),
                                sentence.trim(), Entry.EntryType.MENTION, false));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка индексации книги: " + book.getTitle(), e);
        }
    }
}
