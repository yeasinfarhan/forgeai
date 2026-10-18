package com.forgeai.requirement;

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
 * Content can be edited in DRAFT and IN_REVIEW. APPROVED requirements are locked until reopened,
 * so later phases can rely on them.
 */
public enum RequirementStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED;

    private static final Map<RequirementStatus, Set<RequirementStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(IN_REVIEW),
            IN_REVIEW, Set.of(APPROVED, DRAFT),
            APPROVED, Set.of(DRAFT));

    public boolean canMoveTo(RequirementStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isEditable() {
        return this != APPROVED;
    }
}
