package com.forgeai.code;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CodeFileRepository extends JpaRepository<CodeFile, UUID> {

    List<CodeFile> findAllByProjectIdOrderByPathAsc(UUID projectId);

    Optional<CodeFile> findByProjectIdAndPath(UUID projectId, String path);
}
