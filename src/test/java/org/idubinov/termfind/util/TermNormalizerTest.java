package org.idubinov.termfind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Регрессионные тесты нормализатора (баг: пунктуация не чистилась перед стеммингом).
 */
class TermNormalizerTest {

    @Test
    void punctuationIsStrippedBeforeStemming() {
        assertEquals(TermNormalizer.normalize("тензора"), TermNormalizer.normalize("тензора,"));
        assertEquals(TermNormalizer.normalize("тензор"), TermNormalizer.normalize("(тензор)"));
    }

    @Test
    void casesCollapseToOneForm() {
        assertEquals("тензор", TermNormalizer.normalize("Тензором"));
        assertEquals("тензор", TermNormalizer.normalize("тензорами"));
        assertEquals("тензор", TermNormalizer.normalize("тензор"));
    }

    @Test
    void shortWordsAreKeptAsIs() {
        // короткие слова (<=2 букв) не стеммятся и сохраняются как есть
        assertEquals("он", TermNormalizer.normalize("он"));
        assertEquals("из", TermNormalizer.normalize("Из."));
    }
}
