package org.idubinov.termfind.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.BookRequest;
import org.idubinov.termfind.models.Subject;
import org.idubinov.termfind.repositories.BookRepository;
import org.idubinov.termfind.repositories.BookRequestRepository;
import org.idubinov.termfind.repositories.BookSubjectRepository;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.PageRepository;
import org.idubinov.termfind.repositories.SubjectRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Конвейер заявок: дедуп по SHA-256, проверка «не скан», сохранение заявки.
 */
class BookServiceTest {

    private BookRepository bookRepository;
    private BookRequestRepository bookRequestRepository;
    private SubjectRepository subjectRepository;
    private BookService service;

    @BeforeEach
    void setUp() {
        bookRepository = Mockito.mock(BookRepository.class);
        bookRequestRepository = Mockito.mock(BookRequestRepository.class);
        subjectRepository = Mockito.mock(SubjectRepository.class);
        service = new BookService(bookRepository, bookRequestRepository,
                Mockito.mock(BookSubjectRepository.class), subjectRepository,
                Mockito.mock(PageRepository.class), Mockito.mock(TermRepository.class),
                Mockito.mock(EntryRepository.class));
    }

    /** PDF с английским текстом (Standard14 не умеет кириллицу). */
    private static File pdfWithText(String text) throws IOException {
        File file = File.createTempFile("book_", ".pdf");
        file.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    private static final String RICH_TEXT =
            "A quasimpulse is the wave vector of an electron in a crystal lattice, "
                    + "defined with accuracy up to a reciprocal lattice vector. It plays "
                    + "the role of momentum in solid state physics and band theory.";

    @Test
    void rejectsScanLikePdfWithoutTextLayer() throws IOException {
        when(bookRepository.findByFileHash(anyString())).thenReturn(Optional.empty());
        File scan = pdfWithText("hi"); // < 100 символов — скан

        var outcome = service.createRequest(1L, 1L, scan.toPath(), "scan.pdf");

        assertFalse(outcome.ok());
        assertTrue(outcome.message().contains("скан"));
        verify(bookRequestRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateOfExistingBook() throws IOException {
        when(bookRepository.findByFileHash(anyString())).thenReturn(Optional.of(new Book("Глинский", "x.pdf", 10)));
        File pdf = pdfWithText(RICH_TEXT);

        var outcome = service.createRequest(1L, 1L, pdf.toPath(), "copy.pdf");

        assertFalse(outcome.ok());
        assertTrue(outcome.message().contains("уже есть"));
        verify(bookRequestRepository, never()).save(any());
    }

    @Test
    void acceptsGoodPdfAndCreatesPendingRequest() throws IOException {
        when(bookRepository.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(bookRequestRepository.existsByFileHashAndStatus(anyString(), any())).thenReturn(false);
        when(subjectRepository.findById(7L)).thenReturn(Optional.of(new Subject("Оптика")));

        File pdf = pdfWithText(RICH_TEXT);
        var outcome = service.createRequest(42L, 7L, pdf.toPath(), "optics.pdf");

        assertTrue(outcome.ok());
        assertEquals("Оптика", outcome.subjectName());
        assertNotNull(outcome.request());
        assertEquals(BookRequest.Status.PENDING, outcome.request().getStatus());
        assertEquals(42L, outcome.request().getUserTelegramId());
        verify(bookRequestRepository).save(any(BookRequest.class));
    }
}
