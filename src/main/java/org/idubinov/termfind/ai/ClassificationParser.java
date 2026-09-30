package org.idubinov.termfind.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер JSON-ответа LLM-классификатора: находит объекты {"i":N,"type":"…","score":N}
 * в любом окружающем тексте (LLM любит добавить пояснения), поля выцепляет по отдельности —
 * порядок полей и лишние слова между ними не важны.
 */
public final class ClassificationParser {

    public record Mark(int index, String type, int score) {
    }

    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern INDEX = Pattern.compile("\"i\"\\s*:\\s*(\\d+)");
    private static final Pattern TYPE =
            Pattern.compile("\"type\"\\s*:\\s*\"(DEFINITION|USAGE|NOISE)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE = Pattern.compile("\"score\"\\s*:\\s*(\\d+)");

    private ClassificationParser() {
    }

    public static List<Mark> parse(String llmAnswer) {
        List<Mark> marks = new ArrayList<>();
        if (llmAnswer == null || llmAnswer.isBlank()) return marks;

        Matcher object = OBJECT.matcher(llmAnswer);
        while (object.find()) {
            String block = object.group();
            Matcher index = INDEX.matcher(block);
            Matcher type = TYPE.matcher(block);
            if (!index.find() || !type.find()) continue;

            int score = 5;
            Matcher scoreMatcher = SCORE.matcher(block);
            if (scoreMatcher.find()) {
                score = Math.min(Integer.parseInt(scoreMatcher.group(1)), 10);
            }
            marks.add(new Mark(Integer.parseInt(index.group(1)), type.group(1).toUpperCase(), score));
        }
        return marks;
    }
}
