package com.forgeai.requirement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.forgeai.common.review.ReviewStatus;

/** Request and response bodies of the requirements API. */
public final class RequirementDtos {

    private RequirementDtos() {
    }

    // --- Requests --------------------------------------------------------------

    public record CreateRequirement(
            @NotNull RequirementType type,
            @Size(max = 60) String category,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4_000) String description,
            @NotNull @Size(max = 15) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
            @NotNull Priority priority) {

        RequirementContent content() {
            return RequirementContent.clean(category, title, description, acceptanceCriteria, priority);
        }
    }

    /** {@code version} is the version the client last saw; a mismatch means someone else changed it. */
    public record UpdateRequirement(
            @Size(max = 60) String category,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4_000) String description,
            @NotNull @Size(max = 15) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
            @NotNull Priority priority,
            @NotNull Integer version) {

        RequirementContent content() {
            return RequirementContent.clean(category, title, description, acceptanceCriteria, priority);
        }
    }

    public record ChangeStatus(@NotNull ReviewStatus status, @NotNull Integer version) {
    }

    /** Moves every requirement currently in {@code from} to {@code to}, e.g. "approve everything in review". */
    public record BulkChangeStatus(@NotNull ReviewStatus from, @NotNull ReviewStatus to) {
    }

    // --- Responses -------------------------------------------------------------

    /**
     * @param version  optimistic-locking version; send it back when updating
     * @param revision human-friendly revision number (1 = as created)
     */
    public record RequirementResponse(
            UUID id,
            String code,
            RequirementType type,
            String category,
            String title,
            String description,
            List<String> acceptanceCriteria,
            Priority priority,
            ReviewStatus status,
            RequirementSource source,
            boolean edited,
            int version,
            int revision,
            Instant createdAt,
            Instant updatedAt) {

        static RequirementResponse from(Requirement r) {
            return new RequirementResponse(
                    r.getId(), r.code(), r.getType(), r.getCategory(), r.getTitle(), r.getDescription(),
                    r.acceptanceCriteriaList(), r.getPriority(), r.getStatus(), r.getSource(), r.isEdited(),
                    r.getVersion(), r.getVersion() + 1, r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record RevisionResponse(
            int revision,
            RevisionChange change,
            String category,
            String title,
            String description,
            List<String> acceptanceCriteria,
            Priority priority,
            ReviewStatus status,
            Instant createdAt) {

        static RevisionResponse from(RequirementRevision r) {
            return new RevisionResponse(
                    r.getVersion() + 1, r.getChangeType(), r.getCategory(), r.getTitle(), r.getDescription(),
                    r.acceptanceCriteriaList(), r.getPriority(), r.getStatus(), r.getCreatedAt());
        }
    }

    public record StatusCounts(int draft, int inReview, int approved, int total) {
    }

    /**
     * Everything the requirements page needs.
     *
     * @param briefReady  idea intake has produced a brief, so generation is possible
     * @param allApproved there is at least one requirement and all are approved (later phases need this)
     */
    public record RequirementsOverview(
            List<RequirementResponse> requirements,
            StatusCounts counts,
            boolean briefReady,
            boolean allApproved) {
    }
}
