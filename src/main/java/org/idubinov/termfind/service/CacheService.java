package org.idubinov.termfind.service;

import org.idubinov.termfind.models.LlmCache;
import org.idubinov.termfind.repositories.LlmCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Кэш LLM-ответов в PostgreSQL: ключ sha256(term + отсортированные bookIds + promptType).
 * Добавление книги меняет список bookIds → старые ключи естественно устаревают,
 * TTL 7 дней подчищает остатки.
 */
@Service
public class CacheService {

    private static final Duration TTL = Duration.ofDays(7);

    private final LlmCacheRepository repository;

    public CacheService(LlmCacheRepository repository) {
        this.repository = repository;
    }

    public String keyFor(String term, Collection<Long> bookIds, String promptType) {
        String ids = bookIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return sha256(term + "|" + ids + "|" + promptType);
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findValid(key, LocalDateTime.now()).map(LlmCache::getValue);
    }

    @Transactional
    public void put(String key, String value) {
        LocalDateTime now = LocalDateTime.now();
        repository.save(new LlmCache(key, value, now, now.plus(TTL)));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
