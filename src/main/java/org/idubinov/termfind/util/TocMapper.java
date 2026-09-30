package org.idubinov.termfind.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Разворачивает JSON-оглавление книги (books.toc) в плоский список разделов
 * и сопоставляет страницам разделы («стр. 112 → Глава 3, §3.2»).
 * Чистые функции без LLM: детерминированно и бесплатно.
 */
public final class TocMapper {

    public record TocSection(String title, int startPage) {
    }

    private TocMapper() {
    }

    @SuppressWarnings("unchecked")
    public static List<TocSection> flatten(String tocJson) {
        List<TocSection> result = new ArrayList<>();
        if (tocJson == null || tocJson.isBlank()) return result;
        try {
            List<Object> items = new tools.jackson.databind.ObjectMapper()
                    .readValue(tocJson, List.class);
            walk(items, result);
        } catch (Exception e) {
            // битый toc не должен ломать выдачу — работаем без разделов
            return List.of();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void walk(List<Object> items, List<TocSection> result) {
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            Object title = map.get("t");
            Object page = map.get("p");
            if (title instanceof String t && page instanceof Number n) {
                result.add(new TocSection(t, n.intValue()));
            }
            Object children = map.get("c");
            if (children instanceof List<?> nested) {
                walk((List<Object>) nested, result);
            }
        }
    }

    /** Ближайший раздел, в котором находится страница (последний с startPage <= page). */
    public static String sectionFor(List<TocSection> sections, int page) {
        String found = null;
        for (TocSection section : sections) {
            if (section.startPage() <= page) {
                found = section.title();
            }
        }
        return found;
    }
}
