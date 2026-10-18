package com.forgeai.docs;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the documentation API. */
public final class DocsDtos {

    private DocsDtos() {
    }

    /** Where a file of the project package comes from. */
    public enum Source {
        /** Accepted code. */
        CODE,
        /** A document the AI wrote (README, developer guide). */
        DOCUMENT,
        /** Derived from the approved design when the package is built. */
        DESIGN
    }

    public record UpdateDocument(@NotBlank @Size(max = DocsAssistant.MAX_CHARS) String content, @NotNull Integer version) {
    }

    /**
     * @param generated the AI has written it (otherwise the other fields are empty)
     * @param outdated  the design or the code changed since it was generated
     */
    public record DocumentSummary(
            DocumentKind kind,
            String title,
            String path,
            boolean generated,
            boolean edited,
            boolean outdated,
            String model,
            Instant generatedAt,
            Instant updatedAt) {
    }

    public record DocumentResponse(
            DocumentKind kind,
            String title,
            String path,
            String content,
            boolean edited,
            boolean outdated,
            String model,
            Instant generatedAt,
            Instant updatedAt,
            int version) {
    }

    /**
     * @param replacesCode an accepted code file has the same path; the package contains this one instead
     */
    public record PackageEntry(String path, Source source, int size, boolean replacesCode) {
    }

    public record PackageFile(String path, Source source, String content) {
    }

    /**
     * @param blocker  why documents cannot be written and the package not built yet; null when they can
     * @param fileName the package's file name
     * @param entries  what the package contains, sorted by path (empty while blocked)
     */
    public record DocsOverview(
            boolean canGenerate,
            String blocker,
            List<DocumentSummary> documents,
            String fileName,
            List<PackageEntry> entries) {
    }
}
