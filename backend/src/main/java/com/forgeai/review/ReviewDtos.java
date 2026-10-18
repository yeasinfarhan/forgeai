package com.forgeai.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.forgeai.task.TaskType;

/** Response bodies of the code review API. */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * @param taskId   the task created from the finding (null if none, or if that task was deleted)
     * @param taskCode the code of the task created from the finding
     */
    public record FindingResponse(
            UUID id,
            FindingSeverity severity,
            FindingCategory category,
            String path,
            Integer line,
            String title,
            String detail,
            String suggestion,
            String requirementCode,
            TaskType taskType,
            FindingStatus status,
            UUID taskId,
            String taskCode,
            Instant updatedAt) {
    }

    /** Findings per severity and status. */
    public record FindingCounts(int total, int high, int medium, int low, int open, int dismissed, int taskCreated) {
    }

    /**
     * @param filesReviewed how many files the model was shown (large projects are reviewed partly)
     * @param outdated      the accepted code changed since this review
     */
    public record ReviewResponse(
            UUID id,
            String summary,
            String model,
            Instant createdAt,
            int filesReviewed,
            int filesTotal,
            boolean outdated,
            FindingCounts counts,
            List<FindingResponse> findings) {
    }

    public record ReviewSummary(UUID id, Instant createdAt, String model, int filesReviewed, int filesTotal, FindingCounts counts) {
    }

    /**
     * Everything the review page needs.
     *
     * @param blocker why the code cannot be reviewed now (design not approved, no code); null when it can
     * @param latest  the newest review with its findings, or null before the first one
     */
    public record ReviewOverview(
            boolean canReview,
            String blocker,
            int fileCount,
            List<ReviewSummary> reviews,
            ReviewResponse latest) {
    }
}
