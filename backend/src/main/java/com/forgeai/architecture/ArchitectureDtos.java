package com.forgeai.architecture;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.forgeai.common.review.ReviewStatus;
import com.forgeai.requirement.Priority;
import com.forgeai.requirement.RequirementType;

/** Request and response bodies of the architecture API. */
public final class ArchitectureDtos {

    private ArchitectureDtos() {
    }

    // --- Requests --------------------------------------------------------------

    /** {@code version} is the version the client last saw; a mismatch means someone else changed it. */
    public record UpdateArchitecture(@NotNull @Valid ArchitectureContent content, @NotNull Integer version) {
    }

    public record ChangeArchitectureStatus(@NotNull ReviewStatus status, @NotNull Integer version) {
    }

    // --- Responses -------------------------------------------------------------

    /**
     * @param requirementsChanged   the approved requirements are no longer the ones this content was generated from
     * @param uncoveredRequirements approved requirement codes no component or decision refers to
     * @param version               optimistic-locking version; send it back when changing the architecture
     */
    public record ArchitectureResponse(
            ReviewStatus status,
            ArchitectureContent content,
            String model,
            boolean edited,
            int revision,
            int version,
            boolean requirementsChanged,
            List<String> uncoveredRequirements,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** An approved requirement, as the architecture page shows and references it. */
    public record RequirementRef(String code, RequirementType type, String title, Priority priority) {
    }

    /**
     * Everything the architecture page needs.
     *
     * @param architecture         null until the first proposal is generated
     * @param requirements         the approved requirements, which the architecture is built from
     * @param requirementsTotal    all requirements of the project, approved or not
     * @param requirementsApproved how many of them are approved
     * @param canGenerate          every requirement is approved and the architecture is absent or in draft
     */
    public record ArchitectureOverview(
            ArchitectureResponse architecture,
            List<RequirementRef> requirements,
            int requirementsTotal,
            int requirementsApproved,
            boolean canGenerate) {
    }

    public record RevisionSummary(int revision, ArchitectureChange change, ReviewStatus status, String model, Instant createdAt) {
    }

    public record RevisionDetail(
            int revision,
            ArchitectureChange change,
            ReviewStatus status,
            String model,
            Instant createdAt,
            ArchitectureContent content) {
    }
}
