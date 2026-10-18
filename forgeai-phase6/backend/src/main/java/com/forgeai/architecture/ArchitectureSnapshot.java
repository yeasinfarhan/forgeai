package com.forgeai.architecture;

import com.forgeai.common.review.ReviewStatus;

/**
 * The architecture as later phases (database and API design) see it.
 *
 * @param fingerprint changes whenever the content changes, to detect that a later stage is out of date
 */
public record ArchitectureSnapshot(ReviewStatus status, ArchitectureContent content, String fingerprint) {
}
