package org.idubinov.termfind.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Word-выжимка: структура docx (определения, картинки страниц, источники).
 * PDF создаётся на лету, docx открывается обратно через POI.
 */
class WordServiceTest {

    private static File testPdf;
    private final WordService service = new WordService();

    @BeforeAll
    static void createTestPdf() throws IOException {
        testPdf = File.createTempFile("word_", ".pdf");
        testPdf.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(testPdf);
        }
    }

    private static Entry entry(Entry.EntryType type, int page, String text) {
        Book book = new Book("Глинский. Наноструктуры", testPdf.getAbsolutePath(), 2);
        return new Entry(new Term("тензор", "тенз"), book, page, text, type, false);
    }

    @Test
    void exportsDocxWithDefinitionsAndSources() throws Exception {
        byte[] docx = service.export("тензор",
                List.of(
                        entry(Entry.EntryType.DEFINITION, 1, "Тензор — это объект линейного преобразования."),
                        entry(Entry.EntryType.MENTION, 2, "Тензоры широко применяются в теории упругости.")),
                List.of("• Глава 1 — Введение — со стр. 1"),
                java.util.Map.of());

        assertTrue(docx.length > 1000, "docx не пустой");

        String text;
        int images = 0;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            text = doc.getParagraphs().stream()
                    .map(p -> p.getText())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            images = (int) doc.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .flatMap(r -> r.getEmbeddedPictures().stream())
                    .count();
        }
        assertTrue(text.contains("Тензор — это объект"), "текст определения в документе");
        assertTrue(text.contains("Глинский. Наноструктуры, стр. 1"), "источник с страницей");
        assertTrue(text.contains("Где используется"), "раздел использования");
        assertTrue(text.contains("Глава 1 — Введение"), "TOC-разделы попали в Word");
        assertTrue(images >= 1, "рендер страницы вставлен картинкой");
    }

    @Test
    void fileNameIsSafe() {
        assertEquals("termfind_решётка.docx", WordService.fileName("решётка"));
        assertEquals("termfind_а_б.docx", WordService.fileName("а б/:*?"));
    }

    @Test
    void visionDescriptionAppearsUnderImage() throws Exception {
        Book book = new Book("Глинский. Наноструктуры", testPdf.getAbsolutePath(), 2);
        book.setId(77L);
        Entry definition = new Entry(new Term("тензор", "тенз"), book, 1,
                "Тензор — это объект.", Entry.EntryType.DEFINITION, false);

        byte[] docx = service.export("тензор",
                List.of(definition),
                List.of(),
                java.util.Map.of("77:1", "На странице схема кристаллической решётки."));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("схема кристаллической решётки"), "описание Vision под картинкой");
        }
    }

    @Test
    void strictExportRendersLatexFormulasAsImages() throws Exception {
        String strictAnswer = "Тензор ранга два определяется разложением $A = (e_i \\otimes e_k) A^{ik}$, "
                + "где компоненты преобразуются по закону $A'^{mn} = T^m{}_i T^n{}_k A^{ik}$.";
        byte[] docx = service.exportStrict("тензор", strictAnswer,
                List.of(entry(Entry.EntryType.DEFINITION, 1, "Тензор — это объект.")));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("Строгое определение"));
            assertTrue(text.contains("ранга два"), "текстовый сегмент присутствует");
            assertTrue(text.contains("где компоненты"), "текст между формулами сохранён");
            int images = (int) doc.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .flatMap(r -> r.getEmbeddedPictures().stream())
                    .count();
            assertEquals(2, images, "обе $формулы$ отрендерены картинками");
        }
    }

    @Test
    void strictExportSurvivesBrokenLatex() throws Exception {
        byte[] docx = service.exportStrict("тензор", "Сломанная формула $\\frac{{{!}$ в тексте.",
                List.of(entry(Entry.EntryType.MENTION, 2, "упоминание")));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("Сломанная формула"), "документ собрался, хотя формула не отрендерилась");
        }
    }

    @Test
    void mentionsOnlyStillProducesDocx() throws Exception {
        byte[] docx = service.export("чушь",
                List.of(entry(Entry.EntryType.MENTION, 2, "просто упоминание")),
                List.of(),
                java.util.Map.of());

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("не найдена"), "честно сообщаем про отсутствие определений");
        }
    }
}
