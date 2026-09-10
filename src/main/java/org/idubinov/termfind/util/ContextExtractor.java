package org.idubinov.termfind.util;

import java.util.ArrayList;
import java.util.List;

public class ContextExtractor {

    public record Mention(String term, String context, int pageNumber, boolean isDefinition) {
    }

    private String extractSentenceWithTerm(String paragraph, String lowerSearchTerm) {
        String[] sentences = paragraph.split("(?<=[.!?])\\s+(?=[А-ЯA-Z])");

        for (String sentence : sentences) {
            if (TermNormalizer.containsNormalized(sentence, lowerSearchTerm)){
                return sentence.trim();
            }
        }

        return paragraph.substring(0, Math.min(5, paragraph.length())) + "...";
    }

    public List<Mention> findMentions(String pageText, int pageNumber, String searchTerm) {
        List<Mention> mentions = new ArrayList<>();

        String[] paragraphs = pageText.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
        String lowerSearch = searchTerm.toLowerCase();

        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) continue;

            if (TermNormalizer.containsNormalized(paragraph, lowerSearch)) {
                String context = extractSentenceWithTerm(paragraph.trim(), lowerSearch);

                mentions.add(new Mention(searchTerm, context, pageNumber, false));
            }
        }

        return mentions;
    }
}