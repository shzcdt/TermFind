package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.service.SubjectService;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты шапки/клавиатуры и TXT-отчета (чистые функции, без Telegram-сервера).
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

    // ---------- шапка ----------

    @Test
    void emptyResultSaysNotFound() {
        String header = BotMessageFormatter.buildCard("чушь", List.of(), false);
        assertTrue(header.contains("ничего не найдено"));
    }

    @Test
    void headerContainsStatsAndTeaserDefinitions() {
        String header = BotMessageFormatter.buildCard("тензор",
                List.of(
                        entry(1, Entry.EntryType.DEFINITION, false, 14, "определение тензора"),
                        entry(2, Entry.EntryType.MENTION, false, 50, "упоминание")),
                false);

        assertTrue(header.contains("Найдено: 2"));
        assertTrue(header.contains("определений: 1"));
        assertTrue(header.contains("упоминаний: 1"));
        assertTrue(header.contains("ОПРЕДЕЛЕНИЕ"));
        assertTrue(header.contains("стр. 14")); // лучшее определение
        assertTrue(header.contains("Полный отчет"));
    }

    @Test
    void finalizedHeaderContainsLockNote() {
        String header = BotMessageFormatter.buildCard("тензор",
                List.of(entry(1, Entry.EntryType.DEFINITION, true, 14, "определение")), true);
        assertTrue(header.contains("🔒"));
        assertTrue(header.contains("<b>ТЕНЗОР</b>"));
    }

    // ---------- клавиатура ----------

    @Test
    void nonAdminGetsOnlyViewButtons() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationKeyboard(
                List.of(entry(1, Entry.EntryType.DEFINITION, false, 14, "текст")),
                999L, ADMIN_ID, 7L);
        assertNotNull(keyboard);
        List<String> callbacks = keyboard.getKeyboard().stream()
                .map(row -> row.get(0).getCallbackData()).toList();
        assertEquals(List.of("page:1"), callbacks);
    }

    @Test
    void keyboardHasApproveForBothTypesPlusFinalize() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationKeyboard(
                List.of(
                        entry(1, Entry.EntryType.DEFINITION, false, 14, "определение"),
                        entry(2, Entry.EntryType.MENTION, false, 50, "упоминание"),
                        entry(3, Entry.EntryType.MENTION, true, 51, "подтверждено")),
                ADMIN_ID, ADMIN_ID, 7L);

        assertNotNull(keyboard);
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals("approve:1", rows.get(0).get(0).getCallbackData());
        assertEquals("page:1", rows.get(0).get(1).getCallbackData(), "рядом с апрувом — просмотр страницы");
        assertEquals("approve:2", rows.get(1).get(0).getCallbackData());
        // approved-вхождение: только кнопка просмотра
        assertEquals("page:3", rows.get(2).get(0).getCallbackData());
        assertEquals("finalize:7", rows.get(3).get(0).getCallbackData());
    }

    @Test
    void approvedEntriesStillHaveViewButton() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationKeyboard(
                List.of(entry(1, Entry.EntryType.MENTION, true, 50, "текст")),
                ADMIN_ID, ADMIN_ID, null);
        assertNotNull(keyboard);
        assertEquals("page:1", keyboard.getKeyboard().get(0).get(0).getCallbackData());
    }

    // ---------- TXT-отчет ----------

    @Test
    void reportContainsSectionsAndFullTexts() {
        String longText = "полный текст упоминания ".repeat(30); // длинный текст не должен обрезаться
        byte[] report = ReportExporter.export("решетка", List.of(
                entry(1, Entry.EntryType.DEFINITION, true, 14, "решеткой называется решетка"),
                entry(2, Entry.EntryType.MENTION, false, 50, longText)), 0);

        String content = new String(report, StandardCharsets.UTF_8);
        assertTrue(content.contains("ОТЧЕТ ПО ТЕРМИНУ: решетка"));
        assertTrue(content.contains("Отфильтровано как шум: 0"));
        assertTrue(content.contains("ОПРЕДЕЛЕНИЯ (1)"));
        assertTrue(content.contains("УПОМИНАНИЯ (1)"));
        assertTrue(content.contains("решеткой называется решетка"));
        assertTrue(content.contains(longText.trim()), "полный текст в отчете");
        assertTrue(content.contains("✅ ПОДТВЕРЖДЕНО"));
        assertTrue(content.contains("[1] Глинский, стр. 14"));
    }

    @Test
    void reportFileNameIsSafe() {
        assertEquals("termfind_решетка.txt", ReportExporter.fileName("решетка"));
        assertEquals("termfind_а_б.txt", ReportExporter.fileName("а б/\\:"));
    }

    @Test
    void reportHandlesHundredMentions() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            entries.add(entry(i, Entry.EntryType.MENTION, false, i, "упоминание " + i));
        }
        String content = new String(ReportExporter.export("тензор", entries, 5), StandardCharsets.UTF_8);
        assertTrue(content.contains("упоминание 100"), "все 100 вхождений в одном файле");
        assertTrue(content.contains("УПОМИНАНИЯ (100)"));
    }

    @Test
    void cardEscapesHtmlInTexts() {
        String header = BotMessageFormatter.buildCard("тензор",
                List.of(entry(1, Entry.EntryType.DEFINITION, false, 14, "определение с <тегом> и & символом")),
                false);
        assertFalse(header.contains("<тегом>"));
        assertTrue(header.contains("&lt;тегом&gt;"));
    }

    @Test
    void cardShowsAlternativeDefinitions() {
        String header = BotMessageFormatter.buildCard("тензор",
                List.of(
                        entry(1, Entry.EntryType.DEFINITION, false, 14, "первое определение тензора"),
                        entry(2, Entry.EntryType.DEFINITION, false, 30, "второе определение тензора"),
                        entry(3, Entry.EntryType.MENTION, false, 50, "упоминание")),
                false);
        assertTrue(header.contains("ОПРЕДЕЛЕНИЕ"));
        assertTrue(header.contains("ТАК ЖЕ ГОВОРЯТСЯ"));
        assertTrue(header.contains("стр. 30"));
        assertTrue(header.contains("ВСТРЕЧАЕТСЯ"));
    }

    // ---------- команды ----------

    @Test
    void welcomeExplainsUsageAndCommands() {
        String welcome = BotMessageFormatter.buildWelcome();
        assertTrue(welcome.contains("/subjects"));
        assertTrue(welcome.contains("/help"));
        assertTrue(welcome.contains("термин"));
    }

    @Test
    void subjectsListWithPluralizedBookCounts() {
        String text = BotMessageFormatter.buildSubjects(List.of(
                new SubjectService.SubjectView("Оптика", null, 1),
                new SubjectService.SubjectView("Механика", "общий курс", 3),
                new SubjectService.SubjectView("Квантовая механика", null, 5)));

        assertTrue(text.contains("• Оптика — 1 книга"));
        assertTrue(text.contains("• Механика — общий курс — 3 книги"));
        assertTrue(text.contains("• Квантовая механика — 5 книг"));
    }

    @Test
    void emptySubjectsListHandled() {
        assertEquals("Предметов пока нет.", BotMessageFormatter.buildSubjects(List.of()));
    }
}
