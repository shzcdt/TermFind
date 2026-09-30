package org.idubinov.termfind.service;

import org.idubinov.termfind.models.LlmCache;
import org.idubinov.termfind.repositories.LlmCacheRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    private final LlmCacheRepository repository = Mockito.mock(LlmCacheRepository.class);
    private final CacheService service = new CacheService(repository);

    @Test
    void keyIsStableAndDependsOnTermBooksAndPrompt() {
        String k1 = service.keyFor("тензор", List.of(3L, 1L, 2L), "simpler");
        String k2 = service.keyFor("тензор", List.of(1L, 2L, 3L), "simpler");
        String k3 = service.keyFor("тензор", List.of(1L, 2L, 3L), "stricter");
        String k4 = service.keyFor("решётка", List.of(1L, 2L, 3L), "simpler");

        assertEquals(k1, k2, "порядок bookIds не влияет на ключ");
        assertNotEquals(k1, k3, "тип промпта влияет");
        assertNotEquals(k1, k4, "термин влияет");
        assertEquals(64, k1.length(), "sha256 hex");
    }

    @Test
    void getReturnsValueOnlyIfNotExpired() {
        LocalDateTime now = LocalDateTime.now();
        when(repository.findValid(eq("k1"), any())).thenReturn(Optional.of(new LlmCache("k1", "ответ", now, now.plusDays(7))));
        when(repository.findValid(eq("k2"), any())).thenReturn(Optional.empty());

        assertEquals(Optional.of("ответ"), service.get("k1"));
        assertEquals(Optional.empty(), service.get("k2"));
    }

    @Test
    void putStoresWithSevenDayTtl() {
        service.put("k", "v");
        verify(repository).save(argThat((LlmCache c) ->
                c.getKey().equals("k") && c.getValue().equals("v")
                        && c.getExpiresAt().isAfter(c.getCreatedAt().plusDays(6))));
    }
}
