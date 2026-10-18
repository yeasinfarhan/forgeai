package com.forgeai.design;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DesignRevisionRepository extends JpaRepository<DesignRevision, UUID> {

    List<DesignRevision> findAllByDocumentIdOrderByRevisionDesc(UUID documentId);

    Optional<DesignRevision> findByDocumentIdAndRevision(UUID documentId, int revision);
}
