package com.forgeai.design;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DesignDocumentRepository extends JpaRepository<DesignDocument, UUID> {

    Optional<DesignDocument> findByProjectIdAndKind(UUID projectId, DesignKind kind);
}
