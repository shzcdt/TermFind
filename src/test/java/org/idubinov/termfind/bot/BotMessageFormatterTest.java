package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты форматирования ответов бота (чистые функции, без Telegram-сервера).
 */
class BotMessageFormatterTest {

    private static final long ADMIN_ID = 42L;

    private static Entry entry(long id, Entry.EntryType type, boolean approved, int page, String text) {
        return new Entry(new Term("тензор", "тенз"), new Book("Глинский", "books/x.pdf", 100),
                page, text, type, approved) {
            @Override
            public Long getId() {
                return id;
            }
        };
    }

    @Test
    void emptyResultSaysNotFound() {
        String answer = BotMessageFormatter.formatAnswer("несуществующийтермин", List.of());
        assertTrue(answer.contains("ничего не найдено"));
        assertTrue(answer.contains("несуществующийтермин"));
    }

    @Test
    void definitionsComeFirstThenMentions() {
        List<Entry> entries = List.of(
                entry(1, Entry.EntryType.MENTION, false, 50, "упоминание тензора"),
                entry(2, Entry.EntryType.DEFINITION, false, 14, "тензором ранга N называется величина"));

        String answer = BotMessageFormatter.formatAnswer("тензор", entries);

        int defPos = answer.indexOf("Определения:");
        int mentionPos = answer.indexOf("Упоминания");
        assertTrue(defPos != -1 && mentionPos != -1 && defPos < mentionPos,
                "определения должны идти перед упоминаниями");
        assertTrue(answer.contains("стр. 14"));
        assertTrue(answer.contains("найдено 2 вхождений"));
    }

    @Test
    void moreThanFiveMentionsAreCutWithCounter() {
        List<Entry> entries = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            entries.add(entry(i, Entry.EntryType.MENTION, false, i, "упоминание " + i));
        }

        String answer = BotMessageFormatter.formatAnswer("тензор", entries);

        assertTrue(answer.contains("Упоминания (5 из 8)"));
        assertTrue(answer.contains("упоминание 5"));
        assertFalse(answer.contains("упоминание 6"), "шестое упоминание не должно попасть в ответ");
    }

    @Test
    void longTextIsTruncated() {
        String longText = "х".repeat(1000);
        String answer = BotMessageFormatter.formatAnswer("тензор",
                List.of(entry(1, Entry.EntryType.DEFINITION, false, 1, longText)));

        assertTrue(answer.length() < 1000);
        assertTrue(answer.endsWith("…"));
    }

    @Test
    void approveKeyboardIsNullForNonAdmin() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.approveKeyboard(
                List.of(entry(1, Entry.EntryType.DEFINITION, false, 14, "текст")),
                999L, ADMIN_ID);
        assertNull(keyboard, "у не-админа кнопок модерации быть не должно");
    }

    @Test
    void approveKeyboardContainsOnlyUnapprovedDefinitions() {
        List<Entry> entries = List.of(
                entry(1, Entry.EntryType.DEFINITION, false, 14, "не подтверждено"),
                entry(2, Entry.EntryType.DEFINITION, true, 15, "уже подтверждено"),
                entry(3, Entry.EntryType.MENTION, false, 16, "упоминание"));

        InlineKeyboardMarkup keyboard = BotMessageFormatter.approveKeyboard(entries, ADMIN_ID, ADMIN_ID);

        assertNotNull(keyboard);
        assertEquals(1, keyboard.getKeyboard().size(), "только одна кнопка — одно неподтвержденное определение");
        String callbackData = keyboard.getKeyboard().get(0).get(0).getCallbackData();
        assertEquals("approve:1", callbackData);
    }
}
