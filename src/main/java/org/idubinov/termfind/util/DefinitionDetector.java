package org.idubinov.termfind.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DefinitionDetector {

    public record DefinitionCandidate(String term, String definition, int pageNumber) {
    }

    private static final Pattern DEFINITION_PATTERN = Pattern.compile(
            "([А-Яа-яЁёA-Za-z][А-Яа-яЁёA-Za-z0-9 \\\\-]{2,60}?)" +
                    "\\s*(?:—|–)?\\s*" +
                    "(?:это|называется|называют|представляет собой)" +
                    "[\\s:—\\-]+" +
                    "([^.;]{10,400})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HYPHENATED_LINE_BREAK = Pattern.compile("-\\n");
    private static final Pattern LINE_BREAK = Pattern.compile("\\n+");

    private static final Pattern SENTENCE_TRIM = Pattern.compile("^\\s+|[\\s.,;:–—-]+$");

    public List<DefinitionCandidate> detect(String pageText, int pageNumber) {
        List<DefinitionCandidate> candidates = new ArrayList<>();

        String cleaned = LINE_BREAK.matcher(
                HYPHENATED_LINE_BREAK.matcher(pageText).replaceAll("")).replaceAll(" ");

        Matcher matcher = DEFINITION_PATTERN.matcher(cleaned);

        while (matcher.find()) {
            String term = SENTENCE_TRIM.matcher(matcher.group(1)).replaceAll("");

            String definition = matcher.group(2).trim();

            if (!term.isEmpty() && !definition.isEmpty()) {
                candidates.add(new DefinitionCandidate(term, definition, pageNumber));
            }
        }
        return candidates;
    }

    public List<DefinitionCandidate> detectForTerm(String pageText, int pageNumber, String searchTerm){
        List<DefinitionCandidate> all = detect(pageText, pageNumber);
        return all.stream()
                .filter(c -> c.term().toLowerCase().contains(searchTerm.toLowerCase()))
                .toList();
    }
}
