package com.forgeai.intake;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdeaIntakeRepository extends JpaRepository<IdeaIntake, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IdeaIntake i where i.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
