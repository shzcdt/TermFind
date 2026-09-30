package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.service.EntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Нейро-объяснение термина: RAG поверх нашей БД.
 * LLM получает только наши фрагменты; результат кэшируется в terms.summary.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final LlmClient llmClient;
    private final EntryService entryService;
    private final TermRepository termRepository;

    public SummaryService(LlmClient llmClient, EntryService entryService, TermRepository termRepository) {
        this.llmClient = llmClient;
        this.entryService = entryService;
        this.termRepository = termRepository;
    }

    public boolean isEnabled() {
        return llmClient.isEnabled();
    }

    public record Explanation(String text, String sources, boolean fromCache) {
    }

    /**
     * Объяснение термина. Возвращает кэш, если он есть; иначе вызывает LLM и сохраняет.
     * @return empty, если данных нет или LLM отключен
     */
    @Transactional
    public Optional<Explanation> explain(String query) {
        if (!llmClient.isEnabled()) return Optional.empty();

        Optional<Term> termOpt = entryService.findTermByQuery(query);
        if (termOpt.isEmpty()) return Optional.empty();
        Term term = termOpt.get();

        if (term.getSummary() != null && !term.getSummary().isBlank()) {
            return Optional.of(new Explanation(term.getSummary(), sources(query), true));
        }

        List<Entry> entries = entryService.presentable(query);
        if (entries.isEmpty()) return Optional.empty();

        String answer = llmClient.complete(TermPromptBuilder.SYSTEM_PROMPT,
                TermPromptBuilder.build(query, entries));

        term.setSummary(answer);
        termRepository.save(term);
        log.info("Сгенерировано нейро-объяснение для термина «{}»", query);
        return Optional.of(new Explanation(answer, sources(query), false));
    }

    private String sources(String query) {
        List<Entry> entries = entryService.presentable(query);
        return entries.stream()
                .map(e -> e.getBook().getTitle() + ", стр. " + e.getPageNumber())
                .distinct()
                .limit(10)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    /** Сброс кэша при финализации термина (данные изменились). */
    @Transactional
    public void invalidateCache(Long termId) {
        termRepository.findById(termId).ifPresent(t -> {
            t.setSummary(null);
            termRepository.save(t);
        });
    }
}
