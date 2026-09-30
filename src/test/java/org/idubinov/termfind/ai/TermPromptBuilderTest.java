package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты сборки промпта: grounding — LLM видит только наши фрагменты с метками источников.
 */
class TermPromptBuilderTest {

    private static Entry entry(Entry.EntryType type, int page, String text) {
        return new Entry(new Term("тензор", "тенз"), new Book("Глинский", "books/x.pdf", 100),
                page, text, type, false);
    }

    @Test
    void promptContainsTermSourcesAndFragments() {
        String prompt = TermPromptBuilder.build("тензор", List.of(
                entry(Entry.EntryType.DEFINITION, 14, "тензором ранга N называется величина"),
                entry(Entry.EntryType.MENTION, 50, "упоминание тензора")));

        assertTrue(prompt.contains("Термин: тензор"));
        assertTrue(prompt.contains("(Глинский, стр. 14)"));
        assertTrue(prompt.contains("тензором ранга N называется величина"));
        assertTrue(prompt.contains("УПОМИНАНИЯ ИЗ БАЗЫ"));
    }

    @Test
    void systemPromptForbidsInventing() {
        String system = TermPromptBuilder.SYSTEM_PROMPT;
        assertTrue(system.contains("ТОЛЬКО предоставленные фрагменты"));
        assertTrue(system.contains("Не выдумывай") || system.toLowerCase().contains("не выдум"));
    }

    @Test
    void definitionsLimited() {
        List<Entry> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            many.add(entry(Entry.EntryType.DEFINITION, i, "определение номер " + i));
        }
        String prompt = TermPromptBuilder.build("тензор", many);
        assertFalse(prompt.contains("определение номер 6"), "определений не более 5 в промпте");
    }
}
