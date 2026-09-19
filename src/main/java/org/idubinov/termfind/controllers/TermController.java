package org.idubinov.termfind.controllers;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.service.EntryService;
import org.idubinov.termfind.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terms")
public class TermController {

    private final SearchService searchService;
    private final EntryService entryService;

    public TermController(SearchService searchService, EntryService entryService) {
        this.searchService = searchService;
        this.entryService = entryService;
    }

    /** GET /api/terms/search?term=тензор — возвращает отфильтрованные и отсортированные вхождения. */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(name = "term") String term) {
        long start = System.currentTimeMillis();
        List<Entry> raw = searchService.search(term); // индексирует, если термин новый
        List<Entry> presentable = entryService.presentable(term);

        return Map.of(
                "term", term,
                "found", presentable.size(),
                "filteredOut", raw.size() - presentable.size(),
                "elapsedMs", System.currentTimeMillis() - start,
                "results", presentable.stream().map(e -> Map.of(
                        "type", e.getType().name(),
                        "approved", e.isApproved(),
                        "page", e.getPageNumber(),
                        "book", e.getBook().getTitle(),
                        "text", e.getText()
                )).toList()
        );
    }
}
