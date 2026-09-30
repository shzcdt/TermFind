package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.Entry;

import java.util.List;

/**
 * Собирает промпт для нейро-объяснения термина из вхождений нашей БД.
 * LLM получает только наши тексты — grounding, чтобы не выдумывал источники.
 */
public final class TermPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            Ты — научный консультант для студентов технических вузов.
            Тебе дают термин и фрагменты из учебников (каждый помечен книгой и страницей).
            Правила:
            1. Используй ТОЛЬКО предоставленные фрагменты. Ничего не выдумывай.
            2. Структура ответа: что это такое (по лучшему определению), \
            где встречается в книге (главы/страницы), что читать в первую очередь.
            3. Ссылайся на источники в формате (Автор, стр. N) — бери их из меток фрагментов.
            4. Отвечай по-русски, кратко и по делу, 150-250 слов.""";

    private static final int MAX_DEFINITIONS = 5;
    private static final int MAX_MENTIONS = 15;
    private static final int SNIPPET = 400;

    private TermPromptBuilder() {
    }

    public static String build(String term, List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Термин: ").append(term).append("\n\n");

        sb.append("ОПРЕДЕЛЕНИЯ ИЗ БАЗЫ:\n");
        entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.DEFINITION)
                .limit(MAX_DEFINITIONS)
                .forEach(e -> sb.append("- (").append(e.getBook().getTitle())
                        .append(", стр. ").append(e.getPageNumber()).append(") ")
                        .append(snippet(e.getText())).append('\n'));

        sb.append("\nУПОМИНАНИЯ ИЗ БАЗЫ:\n");
        entries.stream()
                .filter(e -> e.getType() == Entry.EntryType.MENTION)
                .limit(MAX_MENTIONS)
                .forEach(e -> sb.append("- (").append(e.getBook().getTitle())
                        .append(", стр. ").append(e.getPageNumber()).append(") ")
                        .append(snippet(e.getText())).append('\n'));

        return sb.toString();
    }

    private static String snippet(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= SNIPPET ? clean : clean.substring(0, SNIPPET) + "…";
    }
}
