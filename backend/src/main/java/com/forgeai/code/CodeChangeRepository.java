package com.forgeai.code;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CodeChangeRepository extends JpaRepository<CodeChange, UUID> {

    List<CodeChange> findAllByChangesetIdOrderByPositionAsc(UUID changesetId);

    List<CodeChange> findAllByChangesetIdIn(Collection<UUID> changesetIds);

    Optional<CodeChange> findByIdAndChangesetId(UUID id, UUID changesetId);
}
