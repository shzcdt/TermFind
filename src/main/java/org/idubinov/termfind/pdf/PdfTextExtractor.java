package org.idubinov.termfind.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Постраничное извлечение текста из PDF-учебника через Apache PDFBox.
 */
public class PdfTextExtractor {

    public record PageText(int pageNumber, String text) {
        public boolean isEmpty() {
            return text == null || text.isBlank();
        }
    }

    public record BookText(int totalPages, List<PageText> pages) {
    }

    public BookText extract(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalPages = document.getNumberOfPages();
            List<PageText> pages = new ArrayList<>(totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new PageText(page, stripper.getText(document)));
            }
            return new BookText(totalPages, pages);
        }
    }
}
