package org.idubinov.termfind.controllers;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.service.EntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/api/moderation")
public class ModerationController {

    private EntryService entryService;

    @Autowired
    public ModerationController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping("/pending")
    public String pending(@RequestParam(name = "term") String term) {
        List<Entry> entries = entryService.findNotApprovedEntriesByTerm(term);

        return "";
    }
}
