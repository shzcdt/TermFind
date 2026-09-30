package org.idubinov.termfind.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Делит ответ LLM на сегменты текст / $формула$ — для вставки формул в Word картинками.
 * Сегмент либо текстовый (text != null), либо формула (latex != null).
 */
public final class LatexSegments {

    public record Segment(String text, String latex) {
        public static Segment ofText(String text) {
            return new Segment(text, null);
        }

        public static Segment ofLatex(String latex) {
            return new Segment(null, latex);
        }
    }

    private static final Pattern FORMULA = Pattern.compile("\\$([^$]+)\\$");

    private LatexSegments() {
    }

    public static List<Segment> parse(String input) {
        List<Segment> segments = new ArrayList<>();
        if (input == null || input.isBlank()) return segments;

        Matcher matcher = FORMULA.matcher(input);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                segments.add(Segment.ofText(input.substring(last, matcher.start())));
            }
            segments.add(Segment.ofLatex(matcher.group(1)));
            last = matcher.end();
        }
        if (last < input.length()) {
            segments.add(Segment.ofText(input.substring(last)));
        }
        return segments;
    }
}
