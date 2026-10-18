package com.forgeai.code;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CodeChangesetRepository extends JpaRepository<CodeChangeset, UUID> {

    List<CodeChangeset> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<CodeChangeset> findByIdAndProjectId(UUID id, UUID projectId);

    List<CodeChangeset> findAllByProjectIdAndTaskIdAndStatus(UUID projectId, UUID taskId, ChangesetStatus status);
}
