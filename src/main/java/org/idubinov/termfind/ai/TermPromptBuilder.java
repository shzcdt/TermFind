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

    public static final String SIMPLER_PROMPT = """
            Ты — терпеливый преподаватель, объясняющий материал первокурснику.
            Тебе дают термин и фрагменты из учебников. Правила:
            1. Используй ТОЛЬКО предоставленные фрагменты. Ничего не выдумывай.
            2. Объясни термин ПРОЩЕ: без формул, с бытовой аналогией или картинкой в словах.
            3. Отвечай по-русски, 100-150 слов, дружелюбно и без канцелярита.""";

    public static final String STRICTER_PROMPT = """
            Ты — научный руководитель. Тебе дают термин и фрагменты из учебников. Правила:
            1. Используй ТОЛЬКО предоставленные фрагменты. Ничего не выдумывай.
            2. Дай ФОРМАЛЬНОЕ определение термина: строгие формулировки и обозначения.
            3. ВСЕ формулы пиши ТОЛЬКО на LaTeX внутри $...$ (например $A^{ik}$ или $\\vec{p}=\\hbar\\vec{k}$); \
            вне $...$ формульных символов не оставляй — ответ пойдёт в Word с рендером формул.
            4. Ссылайся на источники в формате (Автор, стр. N). Отвечай по-русски, 150-250 слов.""";

    public static final String CLASSIFY_PROMPT = """
            Тебе дают термин и пронумерованные вхождения из учебников.
            Для каждого вхождения определи тип:
            - DEFINITION — содержит определение этого термина;
            - USAGE — осмысленное использование термина, но не определение;
            - NOISE — обрывок текста, мусор, или термин тут ни при чём.
            Ответь СТРОГО JSON-массивом без пояснений:
            [{"i": <номер вхождения>, "type": "DEFINITION|USAGE|NOISE", "score": <уверенность 0-10>}]""";

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

    /** Пронумерованные вхождения для LLM-классификации (номер = позиция с 1). */
    public static String buildNumbered(String term, List<Entry> entries) {
        StringBuilder sb = new StringBuilder("Термин: ").append(term).append("\n\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            sb.append('[').append(i + 1).append("] (")
                    .append(e.getBook().getTitle()).append(", стр. ").append(e.getPageNumber())
                    .append(") ").append(snippet(e.getText(), 300)).append('\n');
        }
        return sb.toString();
    }

    private static String snippet(String text) {
        return snippet(text, 400);
    }

    private static String snippet(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
