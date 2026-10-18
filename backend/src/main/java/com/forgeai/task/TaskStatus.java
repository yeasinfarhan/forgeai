package com.forgeai.task;

/** Board columns. A task can only be started or finished once the tasks it depends on are done. */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
