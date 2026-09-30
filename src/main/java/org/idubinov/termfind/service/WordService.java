package org.idubinov.termfind.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.util.PdfPageRenderer;
import org.idubinov.termfind.util.TocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Word-выжимка по термину (.docx через Apache POI).
 * Всегда «как в учебнике»: только тексты из БД, без LLM-переписываний (FR-13/25).
 * Картинки: рендеры страниц лучших определений, максимум 2 (mitigation тяжёлых файлов).
 */
@Service
public class WordService {

    private static final Logger log = LoggerFactory.getLogger(WordService.class);
    private static final int MAX_IMAGES = 2;
    private static final int IMAGE_WIDTH_PT = 380;

    private final PdfPageRenderer pageRenderer = new PdfPageRenderer();

    /** termfind_решётка.docx — безопасное имя файла. */
    public static String fileName(String term) {
        String safe = term.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return "termfind_" + safe + ".docx";
    }

    public byte[] export(String term, List<Entry> presentable, List<String> usageLines,
                         Map<String, String> schemaDescriptions) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            heading(doc, term, 22);

            List<Entry> definitions = presentable.stream()
                    .filter(e -> e.getType() == Entry.EntryType.DEFINITION)
                    .toList();
            List<Entry> mentions = presentable.stream()
                    .filter(e -> e.getType() == Entry.EntryType.MENTION)
                    .toList();

            heading(doc, "📖 Определение из учебников", 14);
            if (definitions.isEmpty()) {
                paragraph(doc, "Формулировка «как в учебнике» не найдена — см. упоминания ниже.");
            }
            for (Entry definition : definitions) {
                paragraph(doc, definition.getText());
                sourceLine(doc, definition);
            }

            addPageImages(doc, definitions, schemaDescriptions);

            heading(doc, "🔍 Где используется", 14);
            if (usageLines != null && !usageLines.isEmpty()) {
                usageLines.forEach(line -> bullet(doc, line));
            } else if (!mentions.isEmpty()) {
                bullet(doc, "Страницы: " + mentions.stream()
                        .map(Entry::getPageNumber).distinct().limit(20).toList());
            }

            heading(doc, "🔗 Источники", 14);
            sources(doc, presentable);

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось собрать Word: " + e.getMessage(), e);
        }
    }

    private void addPageImages(XWPFDocument doc, List<Entry> definitions, Map<String, String> schemaDescriptions) {
        Set<String> seen = new LinkedHashSet<>();
        int added = 0;
        for (Entry definition : definitions) {
            if (added >= MAX_IMAGES) break;
            String key = definition.getBook().getId() + ":" + definition.getPageNumber();
            if (!seen.add(key)) continue;
            try {
                byte[] png = pageRenderer.renderPage(
                        new File(definition.getBook().getPdfPath()), definition.getPageNumber());
                caption(doc, "🖼 " + definition.getBook().getTitle()
                        + ", стр. " + definition.getPageNumber());
                image(doc, png);
                String description = schemaDescriptions.get(key);
                if (description != null && !description.isBlank()) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun run = p.createRun();
                    run.setText("🧠 " + description.replaceAll("\\s+", " ").trim());
                    run.setItalic(true);
                    run.setFontSize(10);
                }
                added++;
            } catch (Exception e) {
                log.warn("Не удалось вставить страницу {} в Word: {}", key, e.getMessage());
            }
        }
    }

    private void image(XWPFDocument doc, byte[] png) throws Exception {
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(png));
        int height = (int) ((double) buffered.getHeight() / buffered.getWidth() * IMAGE_WIDTH_PT);
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.addPicture(new ByteArrayInputStream(png), Document.PICTURE_TYPE_PNG,
                "page.png", Units.toEMU(IMAGE_WIDTH_PT), Units.toEMU(height));
    }

    private void sources(XWPFDocument doc, List<Entry> entries) {
        Map<String, String> seen = new LinkedHashMap<>();
        int i = 1;
        for (Entry entry : entries) {
            Book book = entry.getBook();
            String line = book.getTitle() + ", стр. " + entry.getPageNumber();
            if (seen.putIfAbsent(book.getId() + ":" + entry.getPageNumber(), line) == null) {
                paragraph(doc, i + ". " + line);
                i++;
            }
            if (i > 15) break;
        }
    }

    private void heading(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(size);
    }

    private void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text.replaceAll("\\s+", " ").trim());
    }

    private void sourceLine(XWPFDocument doc, Entry entry) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(entry.getBook().getTitle() + ", стр. " + entry.getPageNumber());
        run.setItalic(true);
        run.setFontSize(10);
    }

    private void caption(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        p.createRun().setText(text);
    }

    private void bullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(300);
        p.createRun().setText("• " + text);
    }
}
