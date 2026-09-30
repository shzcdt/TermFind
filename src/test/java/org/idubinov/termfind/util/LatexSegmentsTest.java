package org.idubinov.termfind.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LatexSegmentsTest {

    @Test
    void splitsTextAndFormulas() {
        List<LatexSegments.Segment> segments = LatexSegments.parse(
                "Импульс определяется как $\\vec{p}=\\hbar\\vec{k}$, где $\\hbar$ — постоянная.");

        assertEquals(5, segments.size());
        assertEquals("Импульс определяется как ", segments.get(0).text());
        assertNull(segments.get(0).latex());

        assertNull(segments.get(1).text());
        assertEquals("\\vec{p}=\\hbar\\vec{k}", segments.get(1).latex());

        assertEquals(", где ", segments.get(2).text());

        assertNull(segments.get(3).text());
        assertEquals("\\hbar", segments.get(3).latex());

        assertEquals(" — постоянная.", segments.get(4).text());
    }

    @Test
    void textWithoutFormulasIsSingleSegment() {
        List<LatexSegments.Segment> segments = LatexSegments.parse("Просто текст без формул.");
        assertEquals(1, segments.size());
        assertNotNull(segments.get(0).text());
        assertNull(segments.get(0).latex());
    }

    @Test
    void blankInputGivesEmpty() {
        assertTrue(LatexSegments.parse("").isEmpty());
        assertTrue(LatexSegments.parse(null).isEmpty());
    }
}
