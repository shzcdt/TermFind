package org.idubinov.termfind.pdf;

import java.util.ArrayList;
import java.util.List;

public class ContextExtractor {

    public record Mention(String term, String context, int pageNumber, boolean isDefinition){ }

    public List<Mention> findMentions(String pageText, int pageNumber, String searchTerm) {
        List<Mention> results = new ArrayList<>();

        String[] paragraphs = pageText.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");

        String lowerSearch = searchTerm.toLowerCase();

        for (String paragraph : paragraphs){
            if (paragraph.isBlank()) continue;

            if (paragraph.toLowerCase().contains(lowerSearch)) {
                // как-то отделать термин . слова слова термин слова слова.
                String context = paragraph.trim();
                if (context.length() > 500){
                    context = context.substring(0, 500) + "...";
                }

                results.add(new Mention(searchTerm, context, pageNumber, false));
            }
        }

        return results;
    }
}
