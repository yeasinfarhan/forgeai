package com.forgeai.task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.design.DesignDtos.Finding;

/** Request and response bodies of the tasks API. */
public final class TaskDtos {

    private TaskDtos() {
    }

    // --- Requests --------------------------------------------------------------

    /**
     * A task as written by a person. {@code version} is required when updating (the version the
     * client last saw) and ignored when creating.
     */
    public record SaveTask(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4_000) String description,
            @NotNull TaskType type,
            Integer estimate,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 10) String> dependsOn,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 20) String> requirements,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 63) String> entities,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 220) String> endpoints,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> components,
            Integer version) {
    }

    public record ChangeTaskStatus(@NotNull TaskStatus status, @NotNull Integer version) {
    }

    /** Moves a task to {@code position} (0 = top of the backlog). */
    public record MoveTask(@NotNull @Min(0) Integer position, @NotNull Integer version) {
    }

    // --- Responses -------------------------------------------------------------

    /**
     * @param blockedBy codes of the tasks it depends on that are not done yet
     * @param version   optimistic-locking version; send it back when changing the task
     */
    public record TaskResponse(
            UUID id,
            String code,
            int position,
            String title,
            String description,
            TaskType type,
            Integer estimate,
            TaskStatus status,
            TaskSource source,
            boolean edited,
            List<String> acceptanceCriteria,
            List<String> dependsOn,
            List<String> requirements,
            List<String> entities,
            List<String> endpoints,
            List<String> components,
            List<String> blockedBy,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TaskEventResponse(TaskEventType type, TaskStatus fromStatus, TaskStatus toStatus, Instant createdAt) {
    }

    /** @param points sum of estimates; {@code pointsDone} of the done tasks */
    public record TaskCounts(int todo, int inProgress, int done, int total, int points, int pointsDone) {
    }

    /** What tasks can link to: approved requirements and the current design elements. */
    public record DesignRefs(
            List<RequirementRef> requirements,
            List<String> entities,
            List<String> endpoints,
            List<String> components) {
    }

    /**
     * Everything the tasks page needs.
     *
     * @param generatedAt   when the AI last generated tasks; null if never
     * @param inputsChanged the approved design changed after the last generation
     * @param blocker       why generating is not possible right now; null when it is
     * @param findings      coverage and ordering checks
     */
    public record BacklogOverview(
            List<TaskResponse> tasks,
            TaskCounts counts,
            DesignRefs refs,
            boolean canGenerate,
            String blocker,
            Instant generatedAt,
            String model,
            boolean inputsChanged,
            List<Finding> findings) {
    }
}
