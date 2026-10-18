package com.forgeai.design;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.forgeai.common.review.ReviewStatus;

/** Request and response bodies shared by the database and API design endpoints. */
public final class DesignDtos {

    private DesignDtos() {
    }

    public record ChangeDesignStatus(@NotNull ReviewStatus status, @NotNull Integer version) {
    }

    /**
     * A design document with its workflow state.
     *
     * @param inputsChanged the earlier stages it was generated from have changed since
     * @param version       optimistic-locking version; send it back when changing the document
     */
    public record DesignState<T>(
            ReviewStatus status,
            T content,
            String model,
            boolean edited,
            int revision,
            int version,
            boolean inputsChanged,
            Instant createdAt,
            Instant updatedAt) {

        public static <T> DesignState<T> of(DesignDocument document, T content, boolean inputsChanged) {
            return new DesignState<>(document.getStatus(), content, document.getModel(), document.isEdited(),
                    document.getRevision(), document.getVersion(), inputsChanged, document.getCreatedAt(), document.getUpdatedAt());
        }
    }

    public record RevisionSummary(int revision, DesignChange change, ReviewStatus status, String model, Instant createdAt) {
    }

    public record RevisionDetail<T>(int revision, DesignChange change, ReviewStatus status, String model, Instant createdAt, T content) {
    }

    public enum Severity {
        /** Breaks the design: later stages would build on something wrong. */
        ERROR,
        /** Worth a look, but may be intended. */
        WARNING
    }

    /** What a finding is about. */
    public enum Area {
        DATABASE,
        API,
        REQUIREMENTS
    }

    /** One result of the consistency checks between requirements, data model and API. */
    public record Finding(Severity severity, Area area, String message) {

        public static Finding error(Area area, String message) {
            return new Finding(Severity.ERROR, area, message);
        }

        public static Finding warning(Area area, String message) {
            return new Finding(Severity.WARNING, area, message);
        }
    }
}
