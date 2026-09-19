package org.idubinov.termfind.util;

/**
 * Скор уверенности, что текст вхождения — полезный (определение/осмысленное упоминание).
 * Чистые эвристики без NLP: полнота предложения, маркеры определения, длина.
 */
public final class EntryScorer {

    private static final String DEFINITION_MARKERS =
            "(?i)(называется|называют|это |представляет собой|определяется как|называют)";

    private EntryScorer() {
    }

    /** Пороговый фильтр: отсекает обрывки и мусор («как → будет показано дальше»). */
    public static boolean passesFilter(String text) {
        if (text == null || text.isBlank()) return false;
        String clean = text.trim();
        int words = clean.split("\\s+").length;
        boolean endsLikeSentence = clean.matches(".*[А-Яа-яЁёA-Za-z0-9.)»]$")
                || clean.endsWith("…");
        return words >= 4 && endsLikeSentence;
    }

    /** Чем выше, тем вероятнее текст полезен. Диапазон ~0..9. */
    public static int score(String text, boolean isDefinition) {
        if (text == null || text.isBlank()) return 0;
        String clean = text.trim();
        int words = clean.split("\\s+").length;

        int score = 0;

        if (isDefinition && clean.matches(".*" + DEFINITION_MARKERS + ".*")) {
            score += 2; // маркер определения внутри текста
        }
        if (words >= 5) score += 2;       // полнота
        if (words >= 8) score += 1;       // развернутость
        if (clean.matches(".*[.…)»]$") || clean.endsWith("…")) score += 2; // завершенное предложение
        if (Character.isUpperCase(clean.charAt(0))) score += 1;

        // штрафы за мусор
        if (words < 4) score -= 3;        // обрывок
        if (!clean.matches(".*[А-Яа-яЁёA-Za-z]$") && !clean.matches(".*[.…)»]$")
                && !clean.endsWith("…")) score -= 2; // заканчивается на знак/цифру — вероятно оборван

        return score;
    }

    /** Ключ дедупликации: текст без пунктуации и регистра, только стволы слов. */
    public static String dedupKey(String text) {
        return TermNormalizer.normalize(text);
    }
}
