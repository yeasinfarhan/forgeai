package com.forgeai.requirement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RequirementRepository extends JpaRepository<Requirement, UUID> {

    List<Requirement> findAllByProjectIdOrderByTypeAscSeqNumberAsc(UUID projectId);

    Optional<Requirement> findByIdAndProjectId(UUID id, UUID projectId);

    List<Requirement> findAllByProjectIdAndStatus(UUID projectId, RequirementStatus status);

    @Query("select coalesce(max(r.seqNumber), 0) from Requirement r where r.projectId = :projectId and r.type = :type")
    int maxSeqNumber(@Param("projectId") UUID projectId, @Param("type") RequirementType type);

    /**
     * Deletes AI-written drafts nobody has touched: the only requirements regeneration may replace.
     * Their revisions go with them (ON DELETE CASCADE).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Requirement r
            where r.projectId = :projectId
              and r.status = :status
              and r.source = :source
              and r.edited = false
            """)
    int deleteUntouched(
            @Param("projectId") UUID projectId,
            @Param("status") RequirementStatus status,
            @Param("source") RequirementSource source);

    default int deleteReplaceableDrafts(UUID projectId) {
        return deleteUntouched(projectId, RequirementStatus.DRAFT, RequirementSource.AI);
    }
}
