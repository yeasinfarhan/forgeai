package com.forgeai.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewFindingRepository extends JpaRepository<ReviewFinding, UUID> {

    List<ReviewFinding> findAllByReviewIdOrderByPositionAsc(UUID reviewId);

    List<ReviewFinding> findAllByReviewIdIn(Collection<UUID> reviewIds);

    Optional<ReviewFinding> findByIdAndReviewId(UUID id, UUID reviewId);
}
