package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.service.CacheService;
import org.idubinov.termfind.service.EntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM-слой: RAG-объяснение термина, варианты «Проще»/«Строже», классификация вхождений.
 * LLM получает только наши фрагменты (grounding); всё кэшируется:
 * синтез — в terms.summary, варианты и классификация — в llm_cache.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
    /** Классифицируем не больше — остальное стоит токенов без пользы для выдачи. */
    private static final int CLASSIFY_LIMIT = 20;

    public record Explanation(String text, String sources, boolean fromCache) {
    }

    public record Variant(String text, String sources, boolean fromCache) {
    }

    private final LlmClient llmClient;
    private final EntryService entryService;
    private final TermRepository termRepository;
    private final EntryRepository entryRepository;
    private final CacheService cacheService;

    public SummaryService(LlmClient llmClient, EntryService entryService, TermRepository termRepository,
                          EntryRepository entryRepository, CacheService cacheService) {
        this.llmClient = llmClient;
        this.entryService = entryService;
        this.termRepository = termRepository;
        this.entryRepository = entryRepository;
        this.cacheService = cacheService;
    }

    public boolean isEnabled() {
        return llmClient.isEnabled();
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

    /**
     * Вариант объяснения: 💡 Проще (первокурсник, аналогии) или 🔬 Строже (формулы, формально).
     * Ключ кэша: term + bookIds + promptType.
     */
    @Transactional
    public Optional<Variant> variant(String termName, boolean stricter) {
        if (!llmClient.isEnabled()) return Optional.empty();
        List<Entry> entries = entryService.presentable(termName);
        if (entries.isEmpty()) return Optional.empty();

        List<Long> bookIds = entries.stream().map(e -> e.getBook().getId()).distinct().toList();
        String key = cacheService.keyFor(termName, bookIds, stricter ? "stricter" : "simpler");
        Optional<String> cached = cacheService.get(key);
        if (cached.isPresent()) {
            return Optional.of(new Variant(cached.get(), sources(termName), true));
        }

        String system = stricter ? TermPromptBuilder.STRICTER_PROMPT : TermPromptBuilder.SIMPLER_PROMPT;
        String answer = llmClient.complete(system, TermPromptBuilder.build(termName, entries));
        cacheService.put(key, answer);
        log.info("Сгенерирован вариант {} для «{}»", stricter ? "Строже" : "Проще", termName);
        return Optional.of(new Variant(answer, sources(termName), false));
    }

    /**
     * LLM-классификация presentable-вхождений (DEFINITION/USAGE/NOISE + уверенность).
     * Результат пишется в entries.llm_type/llm_score; сам ответ кэшируется.
     * @return сколько вхождений отправлено на классификацию
     */
    @Transactional
    public int classifyTerm(String termName) {
        if (!llmClient.isEnabled()) return 0;
        List<Entry> presentable = entryService.presentable(termName).stream()
                .limit(CLASSIFY_LIMIT)
                .toList();
        if (presentable.isEmpty()) return 0;

        // перечитываем managed-копии: detached-сущности не сохранятся через dirty checking
        List<Long> ids = presentable.stream().map(Entry::getId).toList();
        Map<Long, Entry> byId = entryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Entry::getId, Function.identity()));
        List<Entry> entries = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (entries.isEmpty()) return 0;

        List<Long> bookIds = entries.stream().map(e -> e.getBook().getId()).distinct().toList();
        String key = cacheService.keyFor(termName, bookIds, "classify");
        String answer = cacheService.get(key).orElseGet(() -> {
            String fresh = llmClient.complete(TermPromptBuilder.CLASSIFY_PROMPT,
                    TermPromptBuilder.buildNumbered(termName, entries));
            cacheService.put(key, fresh);
            return fresh;
        });

        for (ClassificationParser.Mark mark : ClassificationParser.parse(answer)) {
            int index = mark.index() - 1;
            if (index >= 0 && index < entries.size()) {
                entries.get(index).setLlmType(mark.type());
                entries.get(index).setLlmScore((double) mark.score());
            }
        }
        log.info("Классифицированы вхождения «{}»: {}", termName, entries.size());
        return entries.size();
    }
}
