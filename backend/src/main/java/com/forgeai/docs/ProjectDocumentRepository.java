package com.forgeai.docs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    List<ProjectDocument> findAllByProjectId(UUID projectId);

    Optional<ProjectDocument> findByProjectIdAndKind(UUID projectId, DocumentKind kind);
}
