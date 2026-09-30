package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.LlmCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface LlmCacheRepository extends JpaRepository<LlmCache, String> {

    @Query("select c from LlmCache c where c.key = :key and c.expiresAt > :now")
    Optional<LlmCache> findValid(@Param("key") String key, @Param("now") LocalDateTime now);
}
