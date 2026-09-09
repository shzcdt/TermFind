package org.idubinov.termfind.util;

import java.util.regex.Pattern;

/** Единая нормализация текста страницы: все, кто ее парсит, должны видеть одинаковый текст. */
public final class TextCleaner {

    private static final Pattern HYPHENATED_LINE_BREAK = Pattern.compile("-\\n");
    private static final Pattern LINE_BREAK = Pattern.compile("\\n+");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+(?=[А-ЯA-Z])");

    private TextCleaner() {
    }

    /** Склеивает переносы слов («шрединге-\nравнение»), осталь­ные \n заменяет пробелами. */
    public static String clean(String pageText) {
        return LINE_BREAK.matcher(
                HYPHENATED_LINE_BREAK.matcher(pageText).replaceAll("")).replaceAll(" ");
    }

    /** Делит чистый текст на предложения (точка/!/… + пробел + заглавная). */
    public static String[] sentences(String cleanedText) {
        return SENTENCE_SPLIT.split(cleanedText);
    }
}
