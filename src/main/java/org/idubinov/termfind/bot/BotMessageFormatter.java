package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.service.SubjectService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тексты и клавиатуры бота. Карточка лёгкая: LLM-ряд, действия и компактная
 * сетка страниц (дедуп по книге+странице, пагинация по 10).
 * Админ-модерация живёт в /moderate, не в карточке.
 */
public final class BotMessageFormatter {

    static final int MAX_MESSAGE_LENGTH = 3800; // запас до 4096
    /** Страниц на один экран карточки: 2 ряда по 5 кнопок. */
    static final int PAGE_GRID_SIZE = 10;
    private static final int PAGE_COLUMNS = 5;

    private BotMessageFormatter() {
    }

    /** Приветствие для /start и /help. */
    public static String buildWelcome() {
        return """
                👋 Привет! Я TermFind — ищу термины в учебниках.

                Пришли термин (например, «квазиимпульс») — найду определения и \
                упоминания в книгах, покажу страницы и соберу выжимку.

                Кнопки под ответом:
                🧠 — нейро-объяснение, 💡 — проще, 🔬 — строже (Word с формулами),
                🖼 — страницы книги, 📄 — Word-файл, 👍/👎 — оценить.

                Команды:
                /subjects — предметы и книги
                /upload — предложить книгу (PDF до 20 МБ)
                /help — эта справка""";
    }

    /** Список предметов с числом книг — для /subjects. */
    public static String buildSubjects(List<SubjectService.SubjectView> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return "Предметов пока нет.";
        }
        StringBuilder sb = new StringBuilder("📚 Предметы и книги:\n");
        for (SubjectService.SubjectView s : subjects) {
            sb.append("• ").append(esc(s.name()));
            if (s.description() != null && !s.description().isBlank()) {
                sb.append(" — ").append(esc(s.description()));
            }
            sb.append(" — ").append(s.bookCount()).append(' ').append(bookWord(s.bookCount())).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Русская плюрализация: 1 книга, 2 книги, 5 книг. */
    private static String bookWord(long n) {
        long mod10 = n % 10, mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "книга";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "книги";
        return "книг";
    }

    /** Клавиатура выбора предмета в диалоге /upload (callback picksub:<id>). */
    public static InlineKeyboardMarkup buildSubjectPickerKeyboard(List<SubjectService.SubjectView> subjects) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (SubjectService.SubjectView s : subjects) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(s.name())
                    .callbackData("picksub:" + s.id())
                    .build()));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    /** Кнопки модерации заявки на книгу: [✅ Одобрить] [❌ Отклонить] (callback reqap/reqre). */
    public static InlineKeyboardMarkup buildRequestAdminKeyboard(long requestId) {
        return buildTwoButtonKeyboard("✅ Одобрить", "reqap:" + requestId, "❌ Отклонить", "reqre:" + requestId);
    }

    /** Клавиатура одного вхождения в /moderate: [✅][⏭] + [🖼 страница]. */
    public static InlineKeyboardMarkup buildModerationEntryKeyboard(long entryId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✅ Подтвердить", "modok:" + entryId),
                button("⏭ Дальше", "modnext")));
        rows.add(List.of(button("🖼 Страница", "page:" + entryId)));
        return new InlineKeyboardMarkup(rows);
    }

    /** Универсальный ряд из двух кнопок. */
    public static InlineKeyboardMarkup buildTwoButtonKeyboard(
            String text1, String callback1, String text2, String callback2) {
        return new InlineKeyboardMarkup(List.of(List.of(
                button(text1, callback1),
                button(text2, callback2))));
    }

    // ---------- карточка термина ----------

    /**
     * Карточка термина (HTML). summaryHtml — уже готовый HTML (mdToHtml), вставляется как есть.
     */
    public static String buildCard(String term, List<Entry> entries, boolean termFinalized) {
        return buildCard(term, entries, termFinalized, null, List.of());
    }

    public static String buildCard(String term, List<Entry> entries, boolean termFinalized,
                                   String summaryHtml, List<String> usageLines) {
        if (entries == null || entries.isEmpty()) {
            return "❌ По запросу «" + esc(term) + "» ничего не найдено.";
        }

        List<Entry> definitions = entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.DEFINITION)
                .toList();
        List<Entry> mentions = entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.MENTION)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("📘 <b>").append(esc(term.toUpperCase())).append("</b>");
        if (termFinalized) sb.append(" 🔒");
        sb.append("\nНайдено: ").append(entries.size())
                .append(" (определений: ").append(definitions.size())
                .append(", упоминаний: ").append(mentions.size()).append(")\n");

        if (!definitions.isEmpty()) {
            Entry best = definitions.get(0);
            sb.append("\n🎯 <b>ОПРЕДЕЛЕНИЕ</b>\n")
                    .append("<i>").append(esc(best.getBook().getTitle()))
                    .append(", стр. ").append(best.getPageNumber()).append("</i>\n")
                    .append(esc(snippet(best.getText(), 600))).append('\n');
        }

        if (summaryHtml != null && !summaryHtml.isBlank()) {
            sb.append("\n🧠 <b>НЕЙРО-ОПРЕДЕЛЕНИЕ</b>\n")
                    .append(snippetHtml(summaryHtml, 700)).append('\n');
        }

        if (definitions.size() > 1) {
            sb.append("\n📌 <b>ТАК ЖЕ ГОВОРЯТСЯ</b>\n");
            definitions.stream().skip(1).limit(2).forEach(e ->
                    sb.append("• «").append(esc(snippet(e.getText(), 90))).append("» — ")
                            .append("<i>").append(esc(e.getBook().getTitle()))
                            .append(", стр. ").append(e.getPageNumber()).append("</i>\n"));
        }

        if (usageLines != null && !usageLines.isEmpty()) {
            sb.append("\n🔍 <b>ГДЕ ИСПОЛЬЗОВАТЬ</b>\n");
            usageLines.forEach(line -> sb.append(esc(line)).append('\n'));
        } else if (!mentions.isEmpty()) {
            List<Integer> pages = mentions.stream().map(Entry::getPageNumber).distinct().sorted().toList();
            sb.append("\n📚 <b>ВСТРЕЧАЕТСЯ</b>: ").append(pageRanges(pages, 12));
            sb.append('\n');
        }

        sb.append("\n📄 Полный отчёт — кнопкой Word ниже");
        return truncate(sb.toString(), MAX_MESSAGE_LENGTH);
    }

    /**
     * Markdown-ответы LLM → Telegram HTML: **x** и __x__ → жирный, *x* → курсив,
     * `x` → код, ### заголовки → жирная строка. Спецсимволы экранируются до тегов.
     */
    public static String mdToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String s = esc(markdown);
        s = s.replaceAll("(?s)\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?s)__(.+?)__", "<b>$1</b>");
        s = s.replaceAll("(?s)\\*(.+?)\\*", "<i>$1</i>");
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        s = s.replaceAll("(?m)^#{1,6}\\s*(.+)$", "<b>$1</b>");
        return s;
    }

    /** Удаление markdown-разметки для Word/plain-текста. */
    public static String stripMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "");
    }

    /** Страницы в диапазоны: [3,4,5,7,13] → «3–5, 7, 13 (+N)». */
    public static String pageRanges(List<Integer> pages, int maxRanges) {
        if (pages == null || pages.isEmpty()) return "";
        List<Integer> sorted = pages.stream().distinct().sorted().toList();
        List<String> ranges = new ArrayList<>();
        int start = sorted.get(0), prev = start;
        for (int i = 1; i <= sorted.size(); i++) {
            Integer current = i < sorted.size() ? sorted.get(i) : Integer.MIN_VALUE;
            if (current != prev + 1) {
                ranges.add(start == prev ? String.valueOf(start) : start + "–" + prev);
                start = current;
            }
            prev = current;
        }
        String shown = String.join(", ", ranges.subList(0, Math.min(maxRanges, ranges.size())));
        if (ranges.size() > maxRanges) {
            shown += " … (+" + (ranges.size() - maxRanges) + " диапазонов)";
        }
        return shown;
    }

    // ---------- клавиатуры ----------

    /**
     * Клавиатура карточки: LLM-ряд, Vision, действия и сетка страниц с пагинацией.
     * Страницы дедуплицируются по (книга, страница), порядок — как в presentable
     * (лучшие первыми), на экране PAGE_GRID_SIZE штук.
     */
    public static InlineKeyboardMarkup buildCardKeyboard(Long termId, boolean llmEnabled, boolean visionEnabled,
                                                         List<Entry> presentable, int pageOffset) {
        if (termId == null) return null;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (llmEnabled) {
            rows.add(List.of(
                    button("🧠 Объяснить", "explain:" + termId),
                    button("💡 Проще", "simpler:" + termId),
                    button("🔬 Строже", "stricter:" + termId)));
        }
        if (visionEnabled) {
            rows.add(List.of(button("🖼 Описать схемы", "vision:" + termId)));
        }
        rows.add(List.of(
                button("📄 Word", "word:" + termId),
                button("👍 Верное", "vote:" + termId + ":up"),
                button("👎 Не то", "vote:" + termId + ":down")));

        List<Entry> pages = distinctPages(presentable);
        int from = Math.max(0, pageOffset);
        int to = Math.min(pages.size(), from + PAGE_GRID_SIZE);
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (Entry entry : pages.subList(from, to)) {
            currentRow.add(button("🖼 " + entry.getPageNumber(), "page:" + entry.getId()));
            if (currentRow.size() == PAGE_COLUMNS) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (from > 0) nav.add(button("◀️ назад", "pages:" + termId + ":" + Math.max(0, from - PAGE_GRID_SIZE)));
        if (to < pages.size()) nav.add(button("▶️ ещё страницы (" + (pages.size() - to) + ")",
                "pages:" + termId + ":" + to));
        if (!nav.isEmpty()) {
            rows.add(nav);
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    /** Дедуп страниц: первая (лучшая) запись на каждую пару книга+страница. */
    public static List<Entry> distinctPages(List<Entry> entries) {
        Map<String, Entry> distinct = new LinkedHashMap<>();
        for (Entry e : entries) {
            distinct.putIfAbsent(e.getBook().getId() + ":" + e.getPageNumber(), e);
        }
        return new ArrayList<>(distinct.values());
    }

    /** [👍 Полезно][👎 Не то] под LLM-сообщением (callback expvote:termId:kind:up|down). */
    public static InlineKeyboardMarkup buildAnswerFeedbackKeyboard(long termId, String kind) {
        return new InlineKeyboardMarkup(List.of(
                List.of(
                        button("👍 Полезно", "expvote:" + termId + ":" + kind + ":up"),
                        button("👎 Не то", "expvote:" + termId + ":" + kind + ":down"))));
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    /** Экранирование HTML-спецсимволов для parseMode=HTML. */
    public static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String snippet(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    /** Как snippet, но сохраняет HTML-теги (для уже отконвертированного markdown). */
    private static String snippetHtml(String html, int max) {
        String clean = html.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
