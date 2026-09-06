package org.idubinov.termfind.util;

import org.tartarus.snowball.ext.russianStemmer;

public class TermNormalizer {

    private static final russianStemmer STEMMER = new russianStemmer();

    public static String normalize(String term){
        if (term == null || term.isBlank()) return "";

        String[] words = term.toLowerCase().trim().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() <= 2){
                result.append(word).append(" ");
                continue;
            }

            word = word.replace("^[^а-яa-z0-9]+|[^а-яa-z0-9]+$", "");

            if (!word.isEmpty()){
                STEMMER.setCurrent(word);
                if (STEMMER.stem()){
                    result.append(STEMMER.getCurrent()).append(" ");
                } else {
                    result.append(word).append(" ");
                }
            }
        }

        return result.toString().trim();
    }
}