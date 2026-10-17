package com.forgeai.intake;

/** Where a project's idea intake stands. {@code NOT_STARTED} is never stored; it means "no intake row". */
public enum IntakeStatus {
    NOT_STARTED,
    AWAITING_ANSWERS,
    BRIEF_READY
}
