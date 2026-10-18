package com.forgeai.review;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.review.ReviewDtos.ReviewOverview;
import com.forgeai.review.ReviewDtos.ReviewResponse;

@RestController
@RequestMapping("/api/projects/{projectId}/reviews")
public class ReviewController {

    private final ReviewService reviews;
    private final ReviewGenerationService generation;

    public ReviewController(ReviewService reviews, ReviewGenerationService generation) {
        this.reviews = reviews;
        this.generation = generation;
    }

    @GetMapping
    public ReviewOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reviews.overview(CurrentUser.id(jwt), projectId);
    }

    /** Reviews the accepted code (calls the AI; can take a few minutes). */
    @PostMapping("/generate")
    public ReviewResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return generation.generate(CurrentUser.id(jwt), projectId);
    }

    @GetMapping("/{reviewId}")
    public ReviewResponse review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID reviewId) {
        return reviews.review(CurrentUser.id(jwt), projectId, reviewId);
    }

    /** Turns a finding into a backlog task. */
    @PostMapping("/{reviewId}/findings/{findingId}/task")
    public ReviewResponse createTask(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID reviewId, @PathVariable UUID findingId) {
        return reviews.createTask(CurrentUser.id(jwt), projectId, reviewId, findingId);
    }

    @PostMapping("/{reviewId}/findings/{findingId}/dismiss")
    public ReviewResponse dismiss(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID reviewId, @PathVariable UUID findingId) {
        return reviews.dismiss(CurrentUser.id(jwt), projectId, reviewId, findingId);
    }

    @PostMapping("/{reviewId}/findings/{findingId}/reopen")
    public ReviewResponse reopen(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID reviewId, @PathVariable UUID findingId) {
        return reviews.reopen(CurrentUser.id(jwt), projectId, reviewId, findingId);
    }
}
