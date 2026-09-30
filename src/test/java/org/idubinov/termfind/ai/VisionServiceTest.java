package org.idubinov.termfind.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.BookImage;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.BookImageRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VisionService: рендер страниц с определениями → LLM → кэш в images.description.
 */
class VisionServiceTest {

    private static File testPdf;

    private BookImageRepository imageRepository;
    private CountingVisionClient llmClient;
    private VisionService service;
    private Book book;

    /** Фейковый vision-клиент: считает вызовы, возвращает фиксированное описание. */
    private static class CountingVisionClient extends LlmClient {
        private int visionCalls = 0;

        CountingVisionClient(boolean enabled) {
            super("http://localhost:0", enabled ? "key" : "", "m", enabled, "vision-m");
        }

        @Override
        public String completeWithImage(String systemPrompt, String userPrompt, byte[] imagePng) {
            visionCalls++;
            return "На странице схема кристаллической решётки.";
        }
    }

    @BeforeAll
    static void createTestPdf() throws IOException {
        testPdf = File.createTempFile("vision_", ".pdf");
        testPdf.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(testPdf);
        }
    }

    @BeforeEach
    void setUp() {
        imageRepository = Mockito.mock(BookImageRepository.class);
        when(imageRepository.findByBookIdAndPageNumber(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        book = new Book("Глинский", testPdf.getAbsolutePath(), 2);
        book.setId(5L);
    }

    private Entry definition(int page) {
        return new Entry(new Term("тензор", "тенз"), book, page,
                "Тензор — это объект.", Entry.EntryType.DEFINITION, false);
    }

    @Test
    void disabledWithoutFlag() {
        service = new VisionService(new CountingVisionClient(false), imageRepository);
        assertTrue(service.describeForTerm("тензор", List.of(definition(1))).isEmpty());
    }

    @Test
    void describesTopTwoDefinitionPagesAndSavesCache() {
        llmClient = new CountingVisionClient(true);
        service = new VisionService(llmClient, imageRepository);

        var schemas = service.describeForTerm("тензор",
                List.of(definition(1), definition(2), definition(1) /* дубль страницы */));

        assertEquals(2, schemas.size(), "лимит 2 схемы на термин");
        assertEquals(2, llmClient.visionCalls);
        assertEquals(5L, schemas.get(0).bookId());
        verify(imageRepository, times(2)).save(any(BookImage.class));
    }

    @Test
    void secondCallUsesCachedDescription() {
        llmClient = new CountingVisionClient(true);
        service = new VisionService(llmClient, imageRepository);
        service.describeForTerm("тензор", List.of(definition(1)));

        // второй запрос: в БД уже лежит описание — LLM больше не зовётся
        BookImage cached = new BookImage(book, 1, "images/5_1.png", "PAGE_RENDER");
        cached.setDescription("На странице схема кристаллической решётки.");
        when(imageRepository.findByBookIdAndPageNumber(5L, 1)).thenReturn(Optional.of(cached));

        var schemas = service.describeForTerm("тензор", List.of(definition(1)));

        assertEquals(1, schemas.size());
        assertEquals(1, llmClient.visionCalls, "описание взято из кэша images.description");
    }
}
