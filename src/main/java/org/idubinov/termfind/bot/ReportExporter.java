package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Собирает TXT-отчет по термину: полные тексты всех определений и упоминаний.
 * Чистая функция — удобно тестировать.
 */
public final class ReportExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ReportExporter() {
    }

    /** Имя файла отчета, например termfind_решетка.txt. */
    public static String fileName(String term) {
        String safe = term.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return "termfind_" + safe + ".txt";
    }

    public static byte[] export(String term, List<Entry> entries, int filteredOut) {
        StringBuilder sb = new StringBuilder();

        long definitions = entries.stream().filter(e -> e.getType() == Entry.EntryType.DEFINITION).count();
        long mentions = entries.size() - definitions;

        sb.append("ОТЧЕТ ПО ТЕРМИНУ: ").append(term).append('\n')
                .append("Дата: ").append(LocalDateTime.now().format(DATE_FMT)).append('\n')
                .append("Показано вхождений: ").append(entries.size())
                .append(" (определений: ").append(definitions)
                .append(", упоминаний: ").append(mentions).append(")\n")
                .append("Отфильтровано как шум: ").append(filteredOut).append('\n')
                .append("=".repeat(60)).append("\n\n");

        sb.append("ОПРЕДЕЛЕНИЯ (").append(definitions).append(")\n")
                .append("-".repeat(60)).append('\n');
        entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.DEFINITION)
                .forEach(e -> appendEntry(sb, e));

        sb.append('\n').append("УПОМИНАНИЯ (").append(mentions).append(")\n")
                .append("-".repeat(60)).append('\n');
        entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.MENTION)
                .forEach(e -> appendEntry(sb, e));

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendEntry(StringBuilder sb, Entry e) {
        sb.append('[').append(e.getId()).append("] ")
                .append(e.getBook().getTitle())
                .append(", стр. ").append(e.getPageNumber())
                .append(e.isApproved() ? "  ✅ ПОДТВЕРЖДЕНО" : "")
                .append('\n')
                .append(normalize(e.getText())).append("\n\n");
    }

    private static String normalize(String text) {
        return text.replaceAll("[ \\t]+", " ").trim();
    }
}
