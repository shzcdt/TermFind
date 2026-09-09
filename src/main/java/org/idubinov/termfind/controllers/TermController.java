package org.idubinov.termfind.controllers;

import org.idubinov.termfind.models.Entry;
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

    public TermController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("term") String term) {
        long start = System.currentTimeMillis();
        List<Entry> entries = searchService.search(term);

        return Map.of(
                "term", term,
                "found", entries.size(),
                "elapsedMs", System.currentTimeMillis() - start,
                "results", entries.stream().map(e -> Map.of(
                        "type", e.getType().name(),
                        "approved", e.isApproved(),
                        "page", e.getPageNumber(),
                        "book", e.getBook().getTitle(),
                        "text", e.getText()
                )).toList()
        );
    }
}
