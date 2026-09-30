package org.idubinov.termfind.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationParserTest {

    @Test
    void parsesCleanJsonArray() {
        List<ClassificationParser.Mark> marks = ClassificationParser.parse(
                "[{\"i\":1,\"type\":\"DEFINITION\",\"score\":9},{\"i\":2,\"type\":\"NOISE\",\"score\":1}]");
        assertEquals(2, marks.size());
        assertEquals(new ClassificationParser.Mark(1, "DEFINITION", 9), marks.get(0));
        assertEquals("NOISE", marks.get(1).type());
    }

    @Test
    void parsesWithSurroundingChatter() {
        List<ClassificationParser.Mark> marks = ClassificationParser.parse(
                "Вот моя классификация:\n```json\n[{\"i\": 3, \"type\": \"usage\", \"score\": 7}]\n```");
        assertEquals(1, marks.size());
        assertEquals("USAGE", marks.get(0).type()); // регистр нормализуем
        assertEquals(3, marks.get(0).index());
    }

    @Test
    void missingScoreDefaultsToFive_andClampsAtTen() {
        List<ClassificationParser.Mark> marks = ClassificationParser.parse(
                "[{\"i\":1,\"type\":\"USAGE\"},{\"i\":2,\"type\":\"DEFINITION\",\"score\":42}]");
        assertEquals(5, marks.get(0).score());
        assertEquals(10, marks.get(1).score(), "score зажимается до 10");
    }

    @Test
    void garbageReturnsEmpty() {
        assertTrue(ClassificationParser.parse("").isEmpty());
        assertTrue(ClassificationParser.parse(null).isEmpty());
        assertTrue(ClassificationParser.parse("LLM отказался отвечать").isEmpty());
    }
}
