package org.idubinov.termfind.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Рендер страницы PDF в PNG — чтобы показать в боте, как определение выглядит в учебнике.
 */
public class PdfPageRenderer {

    private static final float SCALE = 2.0f; // ~144 dpi: читаемо и не тяжело

    public byte[] renderPage(File pdf, int pageNumber) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                throw new IllegalArgumentException(
                        "Страница " + pageNumber + " вне диапазона 1.." + document.getNumberOfPages());
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImage(pageNumber - 1, SCALE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
