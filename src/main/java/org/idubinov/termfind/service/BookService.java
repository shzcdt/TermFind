package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.BookRequest;
import org.idubinov.termfind.models.BookSubject;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Page;
import org.idubinov.termfind.models.Subject;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.BookRepository;
import org.idubinov.termfind.repositories.BookRequestRepository;
import org.idubinov.termfind.repositories.BookSubjectRepository;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.PageRepository;
import org.idubinov.termfind.repositories.SubjectRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.util.DefinitionDetector;
import org.idubinov.termfind.util.PdfTextExtractor;
import org.idubinov.termfind.util.TextCleaner;
import org.idubinov.termfind.util.TocExtractor;
import org.idubinov.termfind.util.TermNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Конвейер книг: заявка (дедуп по SHA-256 + проверка на скан) → одобрение админом
 * → фоновое извлечение: текст страниц в pages, definition-кандидаты в entries,
 * оглавление в books.toc.
 */
@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);
    /** Из первой трёх страниц: меньше символов — перед нами скан без текстового слоя. */
    private static final int SCAN_CHECK_MIN_CHARS = 100;
    private static final int SCAN_CHECK_PAGES = 3;

    public record RequestOutcome(boolean ok, String message, BookRequest request, String subjectName) {
    }

    public record ExtractionStats(int pages, int definitions, boolean tocFound) {
    }

    private final BookRepository bookRepository;
    private final BookRequestRepository bookRequestRepository;
    private final BookSubjectRepository bookSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final PageRepository pageRepository;
    private final TermRepository termRepository;
    private final EntryRepository entryRepository;
    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final DefinitionDetector definitionDetector = new DefinitionDetector();

    public BookService(BookRepository bookRepository, BookRequestRepository bookRequestRepository,
                       BookSubjectRepository bookSubjectRepository, SubjectRepository subjectRepository,
                       PageRepository pageRepository, TermRepository termRepository, EntryRepository entryRepository) {
        this.bookRepository = bookRepository;
        this.bookRequestRepository = bookRequestRepository;
        this.bookSubjectRepository = bookSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.pageRepository = pageRepository;
        this.termRepository = termRepository;
        this.entryRepository = entryRepository;
    }

    /** Заявка на книгу: дедуп по хешу, проверка «не скан», сохранение в book_requests. */
    @Transactional
    public RequestOutcome createRequest(long userTelegramId, long subjectId, Path pdfPath, String displayFileName) {
        String hash;
        try {
            hash = sha256(pdfPath);
        } catch (IOException e) {
            return new RequestOutcome(false, "Не удалось прочитать файл", null, null);
        }

        if (bookRepository.findByFileHash(hash).isPresent()) {
            return new RequestOutcome(false, "Эта книга уже есть в базе 📚", null, null);
        }
        if (bookRequestRepository.existsByFileHashAndStatus(hash, BookRequest.Status.PENDING)) {
            return new RequestOutcome(false, "Такая книга уже на рассмотрении ⏳", null, null);
        }

        PdfTextExtractor.BookText bookText;
        try {
            bookText = pdfTextExtractor.extract(pdfPath.toFile());
        } catch (Exception e) {
            log.warn("Не удалось распарсить PDF от пользователя {}: {}", userTelegramId, e.getMessage());
            return new RequestOutcome(false, "Файл не читается как PDF 😕", null, null);
        }
        int firstPagesChars = bookText.pages().stream()
                .limit(SCAN_CHECK_PAGES)
                .mapToInt(p -> p.text() == null ? 0 : p.text().length())
                .sum();
        if (firstPagesChars < SCAN_CHECK_MIN_CHARS) {
            return new RequestOutcome(false, "Похоже, это скан без текстового слоя — пока не поддерживается 🙈", null, null);
        }

        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return new RequestOutcome(false, "Предмет не найден", null, null);
        }

        BookRequest request = new BookRequest(userTelegramId, subject, hash,
                pdfPath.toString(), displayFileName);
        bookRequestRepository.save(request);
        return new RequestOutcome(true,
                "✅ Заявка отправлена админу — после одобрения книга появится в поиске",
                request, subject.getName());
    }

    /** Одобрение: заявка → books + связь с предметом. Извлечение запускается отдельно (фон). */
    @Transactional
    public Book approve(Long requestId) {
        BookRequest request = bookRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Заявка не найдена"));
        if (request.getStatus() != BookRequest.Status.PENDING) {
            throw new IllegalStateException("Заявка уже обработана");
        }
        request.setStatus(BookRequest.Status.APPROVED);

        Book book = new Book(request.getTitle(), request.getPdfPath(), 0);
        book.setFileHash(request.getFileHash());
        book.setAuthor(request.getAuthor());
        book.setUploadedBy(request.getUserTelegramId());
        bookRepository.save(book);
        bookSubjectRepository.save(new BookSubject(book, request.getSubject()));
        return book;
    }

    /**
     * Фоновое извлечение одобренной книги: страницы в pages (снимок текста),
     * definition-кандидаты → термины + entries, оглавление → books.toc.
     */
    @Transactional
    public ExtractionStats extractBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalStateException("Книга не найдена"));

        PdfTextExtractor.BookText bookText;
        try {
            bookText = pdfTextExtractor.extract(new File(book.getPdfPath()));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать PDF: " + e.getMessage(), e);
        }

        List<Page> pages = new ArrayList<>();
        int definitions = 0;
        for (PdfTextExtractor.PageText pageText : bookText.pages()) {
            if (pageText.isEmpty()) continue;
            String cleaned = TextCleaner.clean(pageText.text());
            pages.add(new Page(book, pageText.pageNumber(), cleaned));

            for (DefinitionDetector.DefinitionCandidate candidate : definitionDetector.detect(cleaned, pageText.pageNumber())) {
                Term term = findOrCreateTerm(candidate.term());
                entryRepository.save(new Entry(term, book, pageText.pageNumber(),
                        candidate.definition(), Entry.EntryType.DEFINITION, false));
                definitions++;
            }
        }
        pageRepository.saveAll(pages);

        book.setTotalPages(bookText.totalPages());
        try {
            String toc = TocExtractor.extractToJson(new File(book.getPdfPath()));
            if (toc != null) book.setToc(toc);
        } catch (IOException e) {
            log.warn("Не удалось извлечь оглавление «{}»: {}", book.getTitle(), e.getMessage());
        }
        bookRepository.save(book);

        // новая книга могла добавить определения известных терминов — их LLM-кэш устарел
        for (Term stale : termRepository.findTermsWithEntriesInBook(bookId)) {
            stale.setSummary(null);
        }

        log.info("Извлечена книга «{}»: {} страниц, {} определений", book.getTitle(), pages.size(), definitions);
        return new ExtractionStats(pages.size(), definitions, book.getToc() != null);
    }

    @Transactional
    public BookRequest reject(Long requestId, String comment) {
        BookRequest request = bookRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Заявка не найдена"));
        request.setStatus(BookRequest.Status.REJECTED);
        request.setAdminComment(comment);
        return request;
    }

    @Transactional(readOnly = true)
    public List<BookRequest> pendingRequests() {
        return bookRequestRepository.findByStatusOrderByIdAsc(BookRequest.Status.PENDING);
    }

    private Term findOrCreateTerm(String displayForm) {
        String base = TermNormalizer.normalize(displayForm);
        final String normalized = base.isEmpty() ? displayForm.toLowerCase().trim() : base;
        return termRepository.findByNormalizedForm(normalized)
                .orElseGet(() -> termRepository.save(new Term(displayForm.trim(), normalized)));
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
