package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.service.SubjectService;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тексты и клавиатуры бота: карточка, сетка страниц, mdToHtml, диапазоны, модерация.
 */
class BotMessageFormatterTest {

    private static final long ADMIN_ID = 42L;

    private static Entry entry(long id, Entry.EntryType type, boolean approved, int page, String text) {
        Book book = new Book("Глинский. Наноструктуры", "books/x.pdf", 100);
        book.setId(1L);
        return new Entry(new Term("тензор", "тенз"), book, page, text, type, approved) {
            @Override
            public Long getId() {
                return id;
            }
        };
    }

    private static List<String> callbacks(InlineKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream()
                .flatMap(row -> row.stream().map(btn -> btn.getCallbackData()))
                .toList();
    }

    // ---------- карточка ----------

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
        assertTrue(header.contains("стр. 14"));
        assertTrue(header.contains("кнопкой Word"));
    }

    @Test
    void mentionsShownAsPageRanges() {
        List<Entry> mentions = new ArrayList<>();
        for (int page : new int[]{3, 4, 5, 7, 13, 14, 20}) {
            mentions.add(entry(page, Entry.EntryType.MENTION, false, page, "упоминание " + page));
        }
        String header = BotMessageFormatter.buildCard("тензор", mentions, false);
        assertTrue(header.contains("3–5, 7, 13–14, 20"), "диапазоны вместо сырого списка, было: " + header);
    }

    @Test
    void summaryIsInsertedAsReadyHtml() {
        String header = BotMessageFormatter.buildCard("тензор",
                List.of(entry(1, Entry.EntryType.DEFINITION, true, 14, "определение")),
                false,
                "<b>Тензор</b> — объект &lt;линейной&gt; алгебры",
                List.of());
        assertTrue(header.contains("<b>Тензор</b>"));
        assertTrue(header.contains("НЕЙРО-ОПРЕДЕЛЕНИЕ"));
    }

    // ---------- mdToHtml / stripMarkdown ----------

    @Test
    void markdownConvertsToTelegramHtml() {
        String html = BotMessageFormatter.mdToHtml("**Тензор** — это *объект*, см. `A^{ik}`");
        assertEquals("<b>Тензор</b> — это <i>объект</i>, см. <code>A^{ik}</code>", html);
    }

    @Test
    void markdownEscapesHtmlBeforeTags() {
        String html = BotMessageFormatter.mdToHtml("**a < b** и &");
        assertTrue(html.contains("<b>a &lt; b</b>"));
        assertTrue(html.contains("&amp;"));
    }

    @Test
    void stripMarkdownRemovesFormatting() {
        assertEquals("Тензор — объект",
                BotMessageFormatter.stripMarkdown("**Тензор** — *объект*"));
    }

    // ---------- клавиатура карточки ----------

    @Test
    void cardKeyboardHasLlmActionsAndDedupedPageGrid() {
        List<Entry> presentable = List.of(
                entry(1, Entry.EntryType.DEFINITION, false, 14, "определение"),
                entry(2, Entry.EntryType.DEFINITION, false, 14, "дубль той же страницы"),
                entry(3, Entry.EntryType.MENTION, false, 50, "упоминание"));

        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildCardKeyboard(
                7L, true, false, presentable, 0);

        assertNotNull(keyboard);
        List<String> data = callbacks(keyboard);
        assertTrue(data.contains("explain:7"));
        assertTrue(data.contains("simpler:7"));
        assertTrue(data.contains("stricter:7"));
        assertTrue(data.contains("word:7"));
        assertTrue(data.contains("vote:7:up"));
        assertTrue(data.contains("vote:7:down"));
        assertTrue(data.contains("page:1"), "первая (лучшая) запись дублированной страницы");
        assertFalse(data.contains("page:2"), "дубликат страницы удалён");
        assertTrue(data.contains("page:3"));
        assertFalse(data.stream().anyMatch(d -> d.startsWith("pages:7:")), "нет пагинации, когда всё влезло");
    }

    @Test
    void cardKeyboardPaginatesPages() {
        List<Entry> presentable = new ArrayList<>();
        for (int page = 1; page <= 15; page++) {
            presentable.add(entry(page, Entry.EntryType.MENTION, false, page, "у" + page));
        }
        InlineKeyboardMarkup first = BotMessageFormatter.buildCardKeyboard(7L, false, false, presentable, 0);
        List<String> firstData = callbacks(first);
        assertEquals(10, firstData.stream().filter(d -> d.startsWith("page:")).count(),
                "10 кнопок страниц на экран");
        assertTrue(firstData.contains("pages:7:10"), "есть ▶️ на следующий экран");
        assertFalse(firstData.contains("pages:7:0"));

        InlineKeyboardMarkup second = BotMessageFormatter.buildCardKeyboard(7L, false, false, presentable, 10);
        List<String> secondData = callbacks(second);
        assertEquals(5, secondData.stream().filter(d -> d.startsWith("page:")).count());
        assertTrue(secondData.contains("pages:7:0"), "есть ◀️ назад");
        assertFalse(secondData.contains("pages:7:20"), "дальше страниц нет");
    }

    @Test
    void visionRowOnlyWhenEnabled() {
        InlineKeyboardMarkup withVision = BotMessageFormatter.buildCardKeyboard(
                7L, true, true, List.of(entry(1, Entry.EntryType.DEFINITION, false, 14, "т")), 0);
        assertTrue(callbacks(withVision).contains("vision:7"));

        InlineKeyboardMarkup withoutVision = BotMessageFormatter.buildCardKeyboard(
                7L, true, false, List.of(entry(1, Entry.EntryType.DEFINITION, false, 14, "т")), 0);
        assertFalse(callbacks(withoutVision).contains("vision:7"));
    }

    @Test
    void nullTermIdGivesNoKeyboard() {
        assertNull(BotMessageFormatter.buildCardKeyboard(null, true, true,
                List.of(entry(1, Entry.EntryType.MENTION, false, 1, "т")), 0));
    }

    // ---------- оценка объяснений и модерация ----------

    @Test
    void answerFeedbackKeyboardCallbacks() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildAnswerFeedbackKeyboard(7L, "EXPLAIN");
        List<String> data = callbacks(keyboard);
        assertTrue(data.contains("expvote:7:EXPLAIN:up"));
        assertTrue(data.contains("expvote:7:EXPLAIN:down"));
    }

    @Test
    void moderationEntryKeyboard() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationEntryKeyboard(99L);
        List<String> data = callbacks(keyboard);
        assertEquals(List.of("modok:99", "modnext", "page:99"), data);
    }

    @Test
    void requestAdminKeyboard() {
        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildRequestAdminKeyboard(5L);
        assertEquals(List.of("reqap:5", "reqre:5"), callbacks(keyboard));
    }

    // ---------- команды ----------

    @Test
    void welcomeExplainsUsageAndCommands() {
        String welcome = BotMessageFormatter.buildWelcome();
        assertTrue(welcome.contains("/subjects"));
        assertTrue(welcome.contains("/upload"));
        assertTrue(welcome.contains("/help"));
        assertTrue(welcome.contains("термин"));
    }

    @Test
    void subjectsListWithPluralizedBookCounts() {
        String text = BotMessageFormatter.buildSubjects(List.of(
                new SubjectService.SubjectView(1, "Оптика", null, 1),
                new SubjectService.SubjectView(2, "Механика", "общий курс", 3),
                new SubjectService.SubjectView(3, "Квантовая механика", null, 5)));

        assertTrue(text.contains("• Оптика — 1 книга"));
        assertTrue(text.contains("• Механика — общий курс — 3 книги"));
        assertTrue(text.contains("• Квантовая механика — 5 книг"));
    }

    @Test
    void emptySubjectsListHandled() {
        assertEquals("Предметов пока нет.", BotMessageFormatter.buildSubjects(List.of()));
    }
}
