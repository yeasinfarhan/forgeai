package com.forgeai.code;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.forgeai.task.TaskStatus;

/** Response bodies of the code API. */
public final class CodeDtos {

    private CodeDtos() {
    }

    public record FileSummary(String path, int lines, int size, String lastTaskCode, Instant updatedAt) {
    }

    public record FileContent(String path, String content, String lastTaskCode, Instant updatedAt) {
    }

    /**
     * @param blockedBy tasks it depends on that are not done; code can be generated once this is empty
     * @param pendingChangesetId the code proposal waiting for review, if any
     * @param hasCode the task's accepted code is in the workspace, so tests can be generated for it
     * @param pendingTestChangesetId the test proposal waiting for review, if any
     */
    public record TaskItem(
            UUID id,
            String code,
            String title,
            TaskStatus status,
            List<String> blockedBy,
            UUID pendingChangesetId,
            boolean hasCode,
            UUID pendingTestChangesetId) {
    }

    public record ChangesetSummary(
            UUID id,
            ChangesetKind kind,
            UUID taskId,
            String taskCode,
            String taskTitle,
            ChangesetStatus status,
            String summary,
            int pending,
            int accepted,
            int rejected,
            Instant createdAt) {
    }

    /**
     * @param stale the file changed after this was proposed, so it can no longer be accepted
     */
    public record ChangeResponse(
            UUID id,
            String path,
            ChangeAction action,
            String baseContent,
            String newContent,
            String explanation,
            ChangeDecision decision,
            boolean stale,
            Instant decidedAt) {
    }

    public record ChangesetResponse(
            UUID id,
            ChangesetKind kind,
            UUID taskId,
            String taskCode,
            String taskTitle,
            ChangesetStatus status,
            String summary,
            String model,
            Instant createdAt,
            Instant completedAt,
            List<ChangeResponse> changes) {
    }

    /**
     * Everything the code page needs.
     *
     * @param blocker why code cannot be generated right now (design not approved, no tasks); null when it can
     */
    public record CodeOverview(
            List<FileSummary> files,
            List<TaskItem> tasks,
            List<ChangesetSummary> changesets,
            boolean canGenerate,
            String blocker) {
    }
}
