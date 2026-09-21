package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Короткое сообщение-шапка (тизер) и клавиатура модерации.
 * Полные тексты уезжают в файл (см. ReportExporter), в чате — только обзор.
 */
public final class BotMessageFormatter {

    static final int MAX_MESSAGE_LENGTH = 3800; // запас до 4096

    private BotMessageFormatter() {
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

        // 🎯 Лучшее определение (approved уже отсортированы первыми)
        if (!definitions.isEmpty()) {
            Entry best = definitions.get(0);
            sb.append("\n🎯 <b>ОПРЕДЕЛЕНИЕ</b>\n")
                    .append("<i>").append(esc(best.getBook().getTitle()))
                    .append(", стр. ").append(best.getPageNumber()).append("</i>\n")
                    .append(esc(snippet(best.getText(), 600))).append('\n');
        }

        // 📌 Альтернативные формулировки — следующие по скору
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
     * Клавиатура модерации: ✅ на каждое неподтвержденное вхождение (оба типа)
     * + 🔒 финализация. Только для админа и только нефинализированных терминов.
     * @param termId id термина для кнопки финализации (null — кнопку не добавлять)
     */
    public static InlineKeyboardMarkup buildModerationKeyboard(List<Entry> entries,
                                                               long viewerId, long adminId, Long termId) {
        if (viewerId != adminId) return null;

        List<List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        for (Entry e : entries) {
            if (e.isApproved()) continue;
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("✅ " + (e.getType() == Entry.EntryType.DEFINITION ? "Опр." : "Упом.")
                            + " стр. " + e.getPageNumber())
                    .callbackData("approve:" + e.getId())
                    .build()));
        }
        if (termId != null) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("🔒 Завершить (удалить неподтвержденные)")
                    .callbackData("finalize:" + termId)
                    .build()));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    private static String snippet(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
