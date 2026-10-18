package com.forgeai.task;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.task.TaskDtos.BacklogOverview;
import com.forgeai.task.TaskDtos.ChangeTaskStatus;
import com.forgeai.task.TaskDtos.MoveTask;
import com.forgeai.task.TaskDtos.SaveTask;
import com.forgeai.task.TaskDtos.TaskEventResponse;

/** The development backlog. Every change returns the whole backlog, since positions and blockers shift. */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService tasks;
    private final TaskGenerationService generation;

    public TaskController(TaskService tasks, TaskGenerationService generation) {
        this.tasks = tasks;
        this.generation = generation;
    }

    @GetMapping
    public BacklogOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return tasks.overview(CurrentUser.id(jwt), projectId);
    }

    /** Generates tasks from the approved design (calls the AI; can take up to a minute). */
    @PostMapping("/generate")
    public BacklogOverview generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return generation.generate(CurrentUser.id(jwt), projectId);
    }

    @PostMapping
    public ResponseEntity<BacklogOverview> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody SaveTask request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks.create(CurrentUser.id(jwt), projectId, request));
    }

    @PutMapping("/{taskId}")
    public BacklogOverview update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody SaveTask request) {
        return tasks.update(CurrentUser.id(jwt), projectId, taskId, request);
    }

    @PostMapping("/{taskId}/status")
    public BacklogOverview changeStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody ChangeTaskStatus request) {
        return tasks.changeStatus(CurrentUser.id(jwt), projectId, taskId, request);
    }

    @PostMapping("/{taskId}/move")
    public BacklogOverview move(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody MoveTask request) {
        return tasks.move(CurrentUser.id(jwt), projectId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    public BacklogOverview delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId) {
        return tasks.delete(CurrentUser.id(jwt), projectId, taskId);
    }

    @GetMapping("/{taskId}/events")
    public List<TaskEventResponse> events(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId) {
        return tasks.events(CurrentUser.id(jwt), projectId, taskId);
    }
}
