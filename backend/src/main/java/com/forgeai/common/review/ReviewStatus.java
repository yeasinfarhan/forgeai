package com.forgeai.common.review;

import java.util.Map;
import java.util.Set;

/**
 * Review workflow:
 *
 * <pre>
 *   DRAFT ──submit──▶ IN_REVIEW ──approve──▶ APPROVED
 *     ▲                  │                      │
 *     └──request changes─┘◀─────────reopen──────┘ (back to DRAFT)
 * </pre>
 *
 * Content can be edited in DRAFT and IN_REVIEW. APPROVED items are locked until reopened,
 * so later phases can rely on them.
 */
public enum ReviewStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED;

    private static final Map<ReviewStatus, Set<ReviewStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(IN_REVIEW),
            IN_REVIEW, Set.of(APPROVED, DRAFT),
            APPROVED, Set.of(DRAFT));

    public boolean canMoveTo(ReviewStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isEditable() {
        return this != APPROVED;
    }

    /** Lower-case label for messages, e.g. "in review". */
    public String label() {
        return switch (this) {
            case DRAFT -> "draft";
            case IN_REVIEW -> "in review";
            case APPROVED -> "approved";
        };
    }
}
