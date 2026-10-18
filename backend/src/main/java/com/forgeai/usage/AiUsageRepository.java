package com.forgeai.usage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    @Query("select coalesce(sum(u.inputTokens + u.outputTokens), 0) from AiUsage u where u.userId = :userId and u.createdAt > :since")
    long sumTokens(@Param("userId") UUID userId, @Param("since") Instant since);

    Optional<AiUsage> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, Instant since);

    List<AiUsage> findAllByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, Instant since);
}
