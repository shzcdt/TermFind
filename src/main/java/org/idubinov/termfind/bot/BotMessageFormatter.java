package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Форматирование ответов бота: текст ответа и клавиатура модерации.
 * Чистые статические функции — удобно тестировать без Telegram.
 */
public final class BotMessageFormatter {

    static final int MAX_MESSAGE_LENGTH = 4000; // лимит Telegram — 4096, с запасом
    static final int MAX_DEFINITIONS = 3;
    static final int MAX_MENTIONS = 5;

    private BotMessageFormatter() {
    }

    public static String formatAnswer(String term, List<Entry> entries) {
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
        sb.append("📘 «").append(term).append("» — найдено ").append(entries.size()).append(" вхождений\n\n");

        if (!definitions.isEmpty()) {
            sb.append("Определения:\n");
            int shown = 0;
            for (Entry e : definitions) {
                if (shown == MAX_DEFINITIONS) {
                    sb.append("… ещё ").append(definitions.size() - shown).append("\n");
                    break;
                }
                sb.append(shown + 1).append(". ").append(e.getBook().getTitle())
                        .append(", стр. ").append(e.getPageNumber()).append(":\n")
                        .append(truncate(e.getText(), 300)).append("\n\n");
                shown++;
            }
        }

        if (!mentions.isEmpty()) {
            sb.append("Упоминания (").append(Math.min(MAX_MENTIONS, mentions.size()))
                    .append(" из ").append(mentions.size()).append("):\n");
            for (int i = 0; i < Math.min(MAX_MENTIONS, mentions.size()); i++) {
                Entry e = mentions.get(i);
                sb.append("• стр. ").append(e.getPageNumber()).append(": ")
                        .append(truncate(e.getText(), 200)).append("\n");
            }
        }

        return truncate(sb.toString(), MAX_MESSAGE_LENGTH);
    }

    /** Клавиатура подтверждения — по кнопке на каждое неподтвержденное определение (только для админа). */
    public static InlineKeyboardMarkup approveKeyboard(List<Entry> entries, long viewerId, long adminId) {
        if (viewerId != adminId) return null;

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Entry e : entries) {
            if (e.getType() != Entry.EntryType.DEFINITION || e.isApproved()) continue;
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("✅ стр. " + e.getPageNumber())
                    .callbackData("approve:" + e.getId())
                    .build()));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    static String truncate(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
