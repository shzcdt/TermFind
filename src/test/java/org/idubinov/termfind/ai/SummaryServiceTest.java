package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.Book;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.EntryRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.idubinov.termfind.service.CacheService;
import org.idubinov.termfind.service.EntryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Тесты кэша и логики SummaryService с фейковым LlmClient.
 */
class SummaryServiceTest {

    interface CompleteFn {
        String apply(String system, String user);
    }

    private static class FakeLlmClient extends LlmClient {
        private final CompleteFn fn;
        private int calls = 0;

        FakeLlmClient(boolean enabled, CompleteFn fn) {
            super("http://localhost:0", enabled ? "key" : "", "test-model");
            this.fn = fn;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            calls++;
            return fn.apply(systemPrompt, userPrompt);
        }
    }

    private static Entry entry(Entry.EntryType type, int page, String text) {
        return new Entry(new Term("тензор", "тенз"), new Book("Глинский", "books/x.pdf", 100),
                page, text, type, false);
    }

    @Test
    void cachesAnswer_secondCallDoesNotHitLlm() {
        LlmClient fake = spy(new FakeLlmClient(true, (s, u) -> "Тензор — это математический объект."));
        EntryService entryService = Mockito.mock(EntryService.class);
        TermRepository termRepo = Mockito.mock(TermRepository.class);
        Term term = new Term("тензор", "тенз");
        when(termRepo.findByNormalizedForm("тенз")).thenReturn(Optional.of(term));
        when(entryService.findTermByQuery("тензор")).thenReturn(Optional.of(term));
        when(entryService.presentable("тензор"))
                .thenReturn(List.of(entry(Entry.EntryType.DEFINITION, 14, "тензором называется величина")));

        SummaryService service = new SummaryService(fake, entryService, termRepo,
                Mockito.mock(EntryRepository.class), Mockito.mock(CacheService.class));

        var first = service.explain("тензор");
        var second = service.explain("тензор");

        assertTrue(first.isPresent() && second.isPresent());
        assertEquals(1, ((FakeLlmClient) fake).calls, "второй вызов должен взять кэш");
        assertEquals(first.get().text(), second.get().text());
        assertTrue(second.get().fromCache());
    }

    @Test
    void disabledWithoutApiKey() {
        LlmClient fake = new FakeLlmClient(false, (s, u) -> "не должно вызываться");
        SummaryService service = new SummaryService(fake,
                Mockito.mock(EntryService.class), Mockito.mock(TermRepository.class),
                Mockito.mock(EntryRepository.class), Mockito.mock(CacheService.class));
        assertFalse(service.isEnabled());
        assertTrue(service.explain("тензор").isEmpty());
    }

    @Test
    void noDataNoCall() {
        LlmClient fake = spy(new FakeLlmClient(true, (s, u) -> "ответ"));
        EntryService entryService = Mockito.mock(EntryService.class);
        Term term = new Term("чушь", "чуш");
        when(entryService.findTermByQuery("чушь")).thenReturn(Optional.of(term));
        when(entryService.presentable("чушь")).thenReturn(List.of());

        SummaryService service = new SummaryService(fake, entryService, Mockito.mock(TermRepository.class),
                Mockito.mock(EntryRepository.class), Mockito.mock(CacheService.class));
        assertTrue(service.explain("чушь").isEmpty());
        assertEquals(0, ((FakeLlmClient) fake).calls);
    }
}
