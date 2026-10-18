package com.forgeai.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findAllByProjectIdOrderByPositionAscSeqNumberAsc(UUID projectId);

    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("select coalesce(max(t.seqNumber), 0) from Task t where t.projectId = :projectId")
    int maxSeqNumber(@Param("projectId") UUID projectId);
}
