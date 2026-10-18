package com.forgeai.requirement;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.forgeai.common.review.ReviewStatus;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;

/**
 * The requirements of a project at one moment, as later phases (architecture, design …) see them.
 * Later phases work from approved requirements only.
 */
public record RequirementSnapshot(List<RequirementResponse> all) {

    public List<RequirementResponse> approved() {
        return all.stream().filter(r -> r.status() == ReviewStatus.APPROVED).toList();
    }

    /**
     * Identifies the approved requirements in their current wording: changes when one is added,
     * edited and re-approved, reopened or deleted.
     */
    public String approvedFingerprint() {
        return Fingerprints.sha256(approved().stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(r -> r.id() + ":" + r.version())
                .collect(Collectors.joining(",")));
    }

    /** The approved requirements as plain text for AI prompts, one per line with code and priority. */
    public String approvedAsPromptText() {
        StringBuilder text = new StringBuilder();
        for (RequirementResponse r : approved()) {
            text.append(r.code()).append(" [").append(r.priority());
            if (r.category() != null && !r.category().isBlank()) text.append(", ").append(r.category());
            text.append("] ").append(r.title()).append(": ").append(r.description()).append('\n');
            if (!r.acceptanceCriteria().isEmpty()) {
                text.append("  Acceptance: ").append(String.join("; ", r.acceptanceCriteria())).append('\n');
            }
        }
        return text.toString().strip();
    }

    /** There is at least one requirement and every requirement is approved. */
    public boolean allApproved() {
        return !all.isEmpty() && all.stream().allMatch(r -> r.status() == ReviewStatus.APPROVED);
    }
}
