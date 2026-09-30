package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Page;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.BookRepository;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.PageRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.util.DefinitionDetector;
import org.idubinov.termfind.util.PdfTextExtractor;
import org.idubinov.termfind.util.TextCleaner;
import org.idubinov.termfind.util.TermNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Поиск термина. Термин ищется по нормализованной форме (стемминг);
 * новый термин лениво индексируется по всем книгам — из снимков страниц (pages),
 * а не из PDF. Книги без снимка (старые) парсятся один раз и сохраняются в pages.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final TermRepository termRepository;
    private final EntryRepository entryRepository;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final DefinitionDetector definitionDetector = new DefinitionDetector();

    @Autowired
    public SearchService(TermRepository termRepository,
                         EntryRepository entryRepository,
                         BookRepository bookRepository,
                         PageRepository pageRepository) {
        this.termRepository = termRepository;
        this.entryRepository = entryRepository;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional
    public List<Entry> search(String query) {
        String normalizedQuery = TermNormalizer.normalize(query);
        if (normalizedQuery.isEmpty()) return List.of();

        Optional<Term> existing = termRepository.findByNormalizedForm(normalizedQuery);
        if (existing.isPresent()) {
            // финализированный термин «запомнен»: переиндексация запрещена,
            // в БД уже остались только утвержденные вхождения
            return entryRepository.findWithBookByTermNormalizedForm(normalizedQuery);
        }

        Term term = termRepository.save(new Term(query.trim(), normalizedQuery));

        for (Book book : bookRepository.findAll()) {
            if (book.getPdfPath() == null) continue;
            try {
                indexTermInBook(book, term, normalizedQuery);
            } catch (Exception e) {
                // ошибка одной книги не должна отменять поиск по остальным
                log.error("Не удалось проиндексировать «{}»: {}", book.getTitle(), e.getMessage());
            }
        }

        return entryRepository.findWithBookByTermNormalizedForm(normalizedQuery);
    }

    private void indexTermInBook(Book book, Term term, String normalizedQuery) throws IOException {
        List<Page> pages = pageRepository.findByBookIdOrderByPageNumber(book.getId());
        if (pages.isEmpty()) {
            pages = extractAndStorePages(book);
        }

        for (Page page : pages) {
            for (var candidate : definitionDetector.detect(page.getCleanedText(), page.getPageNumber())) {
                if (TermNormalizer.containsNormalized(candidate.term(), normalizedQuery)) {
                    entryRepository.save(new Entry(term, book, page.getPageNumber(),
                            candidate.definition(), Entry.EntryType.DEFINITION, false));
                }
            }

            for (String sentence : TextCleaner.sentences(page.getCleanedText())) {
                if (TermNormalizer.containsNormalized(sentence, normalizedQuery)) {
                    entryRepository.save(new Entry(term, book, page.getPageNumber(),
                            sentence.trim(), Entry.EntryType.MENTION, false));
                    break;
                }
            }
        }
    }

    /** Однократный снимок текста книги в pages — дальше поиск читает БД. */
    @Transactional
    public List<Page> extractAndStorePages(Book book) throws IOException {
        PdfTextExtractor.BookText bookText = pdfTextExtractor.extract(new File(book.getPdfPath()));
        List<Page> pages = new ArrayList<>();
        for (PdfTextExtractor.PageText pageText : bookText.pages()) {
            if (pageText.isEmpty()) continue;
            pages.add(new Page(book, pageText.pageNumber(), TextCleaner.clean(pageText.text())));
        }
        pageRepository.saveAll(pages);
        if (book.getTotalPages() == 0) {
            book.setTotalPages(bookText.totalPages());
            bookRepository.save(book);
        }
        log.info("Снимок страниц «{}»: {} страниц сохранено в pages", book.getTitle(), pages.size());
        return pages;
    }
}
