package com.forgeai.review;

/** How much a review finding matters, most severe first. */
public enum FindingSeverity {
    /** A bug, security hole or missing requirement that must be fixed. */
    HIGH,
    /** Should be fixed: a design deviation, unhandled case or missing test. */
    MEDIUM,
    /** Worth improving: readability, naming, small inefficiencies. */
    LOW
}
