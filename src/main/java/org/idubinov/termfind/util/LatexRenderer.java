package org.idubinov.termfind.util;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Рендер LaTeX-формулы в PNG (JLaTeXMath, работает headless).
 * Любая ошибка рендера → null: вызывающий вставит формулу текстом, ничего не падает.
 */
public final class LatexRenderer {

    private LatexRenderer() {
    }

    public static byte[] renderPng(String latex, int fontSizePt) {
        if (latex == null || latex.isBlank()) return null;
        try {
            TeXFormula formula = new TeXFormula(latex.trim());
            TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSizePt);
            BufferedImage image = new BufferedImage(
                    Math.max(icon.getIconWidth(), 1), Math.max(icon.getIconHeight(), 1),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                icon.setForeground(Color.BLACK);
                icon.paintIcon(null, g2, 0, 0);
            } finally {
                g2.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null; // мусорный latex — не роняем документ
        }
    }
}
