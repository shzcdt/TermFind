package org.idubinov.termfind.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Извлечение оглавления из закладок PDF.
 */
class TocExtractorTest {

    @Test
    void pdfWithoutOutlineReturnsNull() throws IOException {
        File file = File.createTempFile("toc_", ".pdf");
        file.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(file);
        }
        assertNull(TocExtractor.extractToJson(file));
    }

    @Test
    void extractsOutlineWithPagesAndEscapesQuotes() throws IOException {
        File file = File.createTempFile("toc_", ".pdf");
        file.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            PDPage page1 = new PDPage();
            PDPage page2 = new PDPage();
            doc.addPage(page1);
            doc.addPage(page2);

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PDOutlineItem ch1 = new PDOutlineItem();
            ch1.setTitle("Глава \"Вводная\"");
            PDPageFitDestination dest1 = new PDPageFitDestination();
            dest1.setPage(page1);
            ch1.setDestination(dest1);
            outline.addLast(ch1);

            PDOutlineItem ch2 = new PDOutlineItem();
            ch2.setTitle("Зонная теория");
            PDPageFitDestination dest2 = new PDPageFitDestination();
            dest2.setPage(page2);
            ch2.setDestination(dest2);
            outline.addLast(ch2);

            doc.save(file);
        }

        String json = TocExtractor.extractToJson(file);
        assertNotNull(json);
        assertTrue(json.contains("Глава \\\"Вводная\\\""), "кавычки в названии экранированы");
        assertTrue(json.contains("\"p\":1"));
        assertTrue(json.contains("Зонная теория"));
        assertTrue(json.contains("\"p\":2"));
    }
}
