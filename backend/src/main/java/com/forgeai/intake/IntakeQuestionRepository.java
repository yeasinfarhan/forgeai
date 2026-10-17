package com.forgeai.intake;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IntakeQuestionRepository extends JpaRepository<IntakeQuestion, UUID> {

    List<IntakeQuestion> findAllByProjectIdOrderByRoundNumberAscPositionIndexAsc(UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IntakeQuestion q where q.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
