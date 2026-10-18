package com.forgeai.requirement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RequirementRevisionRepository extends JpaRepository<RequirementRevision, UUID> {

    List<RequirementRevision> findAllByRequirementIdOrderByVersionDesc(UUID requirementId);
}
