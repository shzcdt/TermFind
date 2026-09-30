package org.idubinov.termfind.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.File;
import java.io.IOException;

/**
 * Извлечение оглавления (закладок PDF) в компактный JSON:
 * [{"t":"Глава 3","p":45,"c":[…]},{…}], p — номер страницы с 1.
 * null = закладок нет (это нормально для части книг).
 */
public final class TocExtractor {

    private TocExtractor() {
    }

    public static String extractToJson(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null || outline.getFirstChild() == null) return null;
            StringBuilder sb = new StringBuilder("[");
            appendItems(document, outline.getFirstChild(), sb);
            sb.append(']');
            return sb.toString();
        }
    }

    private static void appendItems(PDDocument document, PDOutlineItem item, StringBuilder sb) {
        boolean first = true;
        while (item != null) {
            int page = pageNumberOf(document, item);
            if (page > 0) {
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"t\":\"").append(escape(item.getTitle() == null ? "" : item.getTitle()))
                        .append("\",\"p\":").append(page).append(",\"c\":[");
                if (item.hasChildren()) {
                    appendItems(document, item.getFirstChild(), sb);
                }
                sb.append("]}");
            }
            item = item.getNextSibling();
        }
    }

    private static int pageNumberOf(PDDocument document, PDOutlineItem item) {
        try {
            var page = item.findDestinationPage(document);
            if (page == null) return -1;
            int index = document.getPages().indexOf(page);
            return index < 0 ? -1 : index + 1;
        } catch (IOException e) {
            return -1;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }
}
