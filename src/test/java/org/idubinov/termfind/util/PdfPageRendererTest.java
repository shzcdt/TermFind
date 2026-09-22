package org.idubinov.termfind.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты рендера страницы PDF в PNG. PDF создается на лету средствами PDFBox.
 */
class PdfPageRendererTest {

    private static File testPdf;

    @BeforeAll
    static void createTestPdf() throws IOException {
        testPdf = File.createTempFile("test_", ".pdf");
        testPdf.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(testPdf);
        }
    }

    @Test
    void rendersPageAsPng() throws IOException {
        byte[] png = new PdfPageRenderer().renderPage(testPdf, 1);

        assertTrue(png.length > 100, "PNG не должен быть пустым");
        // магическое число PNG: 89 50 4E 47
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }

    @Test
    void rendersSecondPage() throws IOException {
        assertDoesNotThrow(() -> new PdfPageRenderer().renderPage(testPdf, 2));
    }

    @Test
    void rejectsPageOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new PdfPageRenderer().renderPage(testPdf, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new PdfPageRenderer().renderPage(testPdf, 0));
    }
}
