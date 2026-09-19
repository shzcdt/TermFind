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

    /** Короткая шапка-карточка: лучшее определение + страницы упоминаний + статистика. */
    public static String buildHeader(String term, List<Entry> entries, boolean termFinalized) {
        if (entries == null || entries.isEmpty()) {
            return "❌ По запросу «" + term + "» ничего не найдено.";
        }

        List<Entry> definitions = entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.DEFINITION)
                .toList();
        List<Entry> mentions = entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.MENTION)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("📘 «").append(term).append("» — найдено ").append(entries.size())
                .append(" вхождений (определений: ").append(definitions.size())
                .append(", упоминаний: ").append(mentions.size()).append(")\n");
        if (termFinalized) {
            sb.append("🔒 Термин финализирован — показаны только утвержденные вхождения\n");
        }

        // 🎯 Лучшее определение: approved уже отсортированы первыми, берем первое
        if (!definitions.isEmpty()) {
            Entry best = definitions.get(0);
            sb.append("\n🎯 Лучшее определение (").append(best.getBook().getTitle())
                    .append(", стр. ").append(best.getPageNumber()).append("):\n")
                    .append(snippet(best.getText(), 400)).append('\n');
        }

        // 📚 Где встречается: страницы упоминаний
        if (!mentions.isEmpty()) {
            sb.append("\n📚 Также встречается: ");
            sb.append(mentions.stream()
                    .map(e -> String.valueOf(e.getPageNumber()))
                    .distinct()
                    .limit(12)
                    .collect(java.util.stream.Collectors.joining(", ")));
            long totalPages = mentions.stream().map(Entry::getPageNumber).distinct().count();
            if (totalPages > 12) sb.append("…");
            sb.append('\n');
        }

        sb.append("\n📄 Полный отчет — во вложенном файле\n");
        return truncate(sb.toString(), MAX_MESSAGE_LENGTH);
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
