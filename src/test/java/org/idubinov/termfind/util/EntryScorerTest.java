package org.idubinov.termfind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntryScorerTest {

    @Test
    void strongDefinitionScoresHigherThanFragment() {
        String good = "Тензором ранга N называется величина, которая в произвольной декартовой "
                + "системе координат однозначно характеризуется своими компонентами.";
        String fragment = "будет показано дальше";

        assertTrue(EntryScorer.score(good, true) > EntryScorer.score(fragment, false));
    }

    @Test
    void filterRejectsKnownJunk() {
        assertFalse(EntryScorer.passesFilter("будет показано дальше,"));
        assertFalse(EntryScorer.passesFilter("тензор"));
        assertFalse(EntryScorer.passesFilter(""));
        assertFalse(EntryScorer.passesFilter(null));
    }

    @Test
    void filterAcceptsCompleteSentence() {
        assertTrue(EntryScorer.passesFilter(
                "Решеткой Браве называется набор векторов трансляции, описывающих периодичность кристалла."));
    }

    @Test
    void definitionMarkerAddsScore() {
        String withMarker = "Тензором называется величина, преобразующаяся по специальному закону при смене базиса.";
        String sameWithout = "Величина, преобразующаяся по специальному закону при смене базиса.";

        assertTrue(EntryScorer.score(withMarker, true) > EntryScorer.score(sameWithout, true));
    }

    @Test
    void incompleteEndingIsPenalized() {
        String complete = "Решетка Браве описывает трансляционную симметрию кристалла.";
        String broken = "решетка Браве описывает трансляционную симметрию кристалла, в ко";

        assertTrue(EntryScorer.score(complete, false) > EntryScorer.score(broken, false));
    }
}
