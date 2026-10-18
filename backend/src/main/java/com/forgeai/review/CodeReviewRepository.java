package com.forgeai.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CodeReviewRepository extends JpaRepository<CodeReview, UUID> {

    List<CodeReview> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<CodeReview> findByIdAndProjectId(UUID id, UUID projectId);
}
