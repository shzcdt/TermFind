package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.service.SubjectService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Короткое сообщение-шапка (тизер) и клавиатура модерации.
 * Полные тексты уезжают в файл (см. ReportExporter), в чате — только обзор.
 * Здесь же — тексты команд /start, /help и /subjects.
 */
public final class BotMessageFormatter {

    static final int MAX_MESSAGE_LENGTH = 3800; // запас до 4096

    private BotMessageFormatter() {
    }

    /** Приветствие для /start и /help. */
    public static String buildWelcome() {
        return """
                👋 Привет! Я TermFind — ищу термины в учебниках.

                Пришли термин (например, «квазиимпульс») — найду определения и \
                упоминания в книгах, покажу страницы и пришлю полный отчёт файлом.

                Кнопки под ответом:
                🖼 — показать страницу книги, 🧠 — объяснение от нейросети.

                Команды:
                /subjects — предметы и книги
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
        List<List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();
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
        return new InlineKeyboardMarkup(List.of(
                List.of(
                        InlineKeyboardButton.builder()
                                .text("✅ Одобрить")
                                .callbackData("reqap:" + requestId)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("❌ Отклонить")
                                .callbackData("reqre:" + requestId)
                                .build())));
    }

    /**
     * Карточка термина (HTML): заголовок, лучшее определение, альтернативные формулировки,
     * страницы упоминаний. Экранирование HTML — обязанность вызывающего текста, здесь экранируем сами.
     */
    public static String buildCard(String term, List<Entry> entries, boolean termFinalized) {
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

        if (definitions.size() > 1) {
            sb.append("\n📌 <b>ТАК ЖЕ ГОВОРЯТСЯ</b>\n");
            definitions.stream().skip(1).limit(2).forEach(e ->
                    sb.append("• «").append(esc(snippet(e.getText(), 90))).append("» — ")
                            .append("<i>").append(esc(e.getBook().getTitle()))
                            .append(", стр. ").append(e.getPageNumber()).append("</i>\n"));
        }

        // 📚 Где встречается
        if (!mentions.isEmpty()) {
            List<Integer> pages = mentions.stream().map(Entry::getPageNumber).distinct().toList();
            sb.append("\n📚 <b>ВСТРЕЧАЕТСЯ</b>: ");
            pages.stream().limit(12).forEach(p -> sb.append(p).append(", "));
            sb.setLength(sb.length() - 2);
            if (pages.size() > 12) sb.append("… (+").append(pages.size() - 12).append(")");
            sb.append('\n');
        }

        sb.append("\n📄 Полный отчет — во вложенном файле");
        return truncate(sb.toString(), MAX_MESSAGE_LENGTH);
    }

    /** Экранирование HTML-спецсимволов для parseMode=HTML. */
    public static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Клавиатура: строка на каждое неподтвержденное вхождение — [✅ апрув] [🖼 показать страницу],
     * для approved — только [🖼]. Отдельной строкой — 🔒 финализация.
     * Только для админа; кнопка 🖼 должна быть видна всем — для нее см. buildViewKeyboard.
     * @param termId id термина для кнопки финализации (null — кнопку не добавлять)
     */
    public static InlineKeyboardMarkup buildModerationKeyboard(List<Entry> entries,
                                                               long viewerId, long adminId, Long termId) {
        if (viewerId != adminId) return buildViewKeyboard(entries);

        List<List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        for (Entry e : entries) {
            List<InlineKeyboardButton> row = new java.util.ArrayList<>(2);
            if (!e.isApproved()) {
                row.add(InlineKeyboardButton.builder()
                        .text("✅ " + (e.getType() == Entry.EntryType.DEFINITION ? "Опр." : "Упом.")
                                + " стр. " + e.getPageNumber())
                        .callbackData("approve:" + e.getId())
                        .build());
            }
            row.add(viewPageButton(e));
            rows.add(row);
        }
        if (termId != null) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("🔒 Завершить (удалить неподтвержденные)")
                    .callbackData("finalize:" + termId)
                    .build()));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    /** Только кнопки просмотра страниц — для обычных пользователей. */
    public static InlineKeyboardMarkup buildViewKeyboard(List<Entry> entries) {
        List<List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        for (Entry e : entries) {
            rows.add(List.of(viewPageButton(e)));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton viewPageButton(Entry e) {
        return InlineKeyboardButton.builder()
                .text("🖼 стр. " + e.getPageNumber())
                .callbackData("page:" + e.getId())
                .build();
    }

    private static String snippet(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}