package com.forgeai.review;

/** What a person did with a review finding. */
public enum FindingStatus {
    OPEN,
    DISMISSED,
    /** Turned into a backlog task. */
    TASK_CREATED
}
