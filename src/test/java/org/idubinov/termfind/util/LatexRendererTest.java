package org.idubinov.termfind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Рендер LaTeX в PNG под headless: валидная формула → PNG, мусор → null.
 */
class LatexRendererTest {

    @Test
    void rendersValidFormulaAsPng() {
        byte[] png = LatexRenderer.renderPng("\\frac{A^{ik}}{2} \\otimes e_k", 18);
        assertNotNull(png, "формула рендерится headless");
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
        assertTrue(png.length > 200);
    }

    @Test
    void garbageLatexDoesNotThrow() {
        // JLaTeXMath прощает часть мусора (рисует что может) — главное, не исключение:
        // либо null (ошибка рендера), либо валидный PNG
        byte[] result = assertDoesNotThrow(() -> LatexRenderer.renderPng("\\frac{{{!}", 18));
        if (result != null) {
            assertEquals((byte) 0x89, result[0]);
        }
        assertNull(LatexRenderer.renderPng(null, 18));
        assertNull(LatexRenderer.renderPng("   ", 18));
    }
}
