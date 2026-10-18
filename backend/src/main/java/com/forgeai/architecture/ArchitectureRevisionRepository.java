package com.forgeai.architecture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ArchitectureRevisionRepository extends JpaRepository<ArchitectureRevision, UUID> {

    List<ArchitectureRevision> findAllByProjectIdOrderByRevisionDesc(UUID projectId);

    Optional<ArchitectureRevision> findByProjectIdAndRevision(UUID projectId, int revision);
}
