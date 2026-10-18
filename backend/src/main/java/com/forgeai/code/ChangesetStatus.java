package com.forgeai.code;

/**
 * PENDING while some file is undecided; COMPLETED once every file is accepted or rejected;
 * DISCARDED when a person dropped the rest, or a newer proposal for the same task replaced it.
 */
public enum ChangesetStatus {
    PENDING,
    COMPLETED,
    DISCARDED
}
