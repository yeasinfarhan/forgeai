package com.forgeai.requirement;

import java.util.List;

import com.forgeai.common.review.ReviewStatus;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;

/**
 * The requirements of a project at one moment, as later phases (architecture, design …) see them.
 * Later phases work from approved requirements only.
 */
public record RequirementSnapshot(List<RequirementResponse> all) {

    public List<RequirementResponse> approved() {
        return all.stream().filter(r -> r.status() == ReviewStatus.APPROVED).toList();
    }

    /** There is at least one requirement and every requirement is approved. */
    public boolean allApproved() {
        return !all.isEmpty() && all.stream().allMatch(r -> r.status() == ReviewStatus.APPROVED);
    }
}
