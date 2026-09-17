package org.idubinov.termfind.controllers;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.service.EntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final EntryService entryService;

    @Autowired
    public ModerationController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping("/pending")
    public Map<String, Object> pending(@RequestParam(name = "term") String term) {
        List<Entry> entries = entryService.findNotApprovedEntriesByTerm(term);

        return Map.of(
                "term", term,
                "pending", entries.size(),
                "results", entries.stream().map(e -> Map.of(
                        "id", e.getId(),
                        "type", e.getType().name(),
                        "page", e.getPageNumber(),
                        "book", e.getBook().getTitle(),
                        "text", e.getText()
                )).toList()
        );
    }


    @PostMapping("/approve/{id}")
    public Map<String, Object> approve(@PathVariable Long id) {
        boolean approved = entryService.approveEntry(id);
        return Map.of(
                "id", id,
                "approved", approved
        );
    }
}
