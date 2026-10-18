package com.forgeai.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.architecture.ArchitectureContent;
import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.architecture.ArchitectureService;
import com.forgeai.architecture.ArchitectureSnapshot;
import com.forgeai.common.exception.BadRequestException;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.design.api.ApiDesignContent;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.design.api.ApiDtos.ApiSnapshot;
import com.forgeai.design.database.DatabaseDesignContent;
import com.forgeai.design.database.DatabaseDesignService;
import com.forgeai.design.database.DatabaseDtos.DatabaseSnapshot;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;
import com.forgeai.requirement.RequirementType;
import com.forgeai.task.TaskDtos.BacklogOverview;
import com.forgeai.task.TaskDtos.ChangeTaskStatus;
import com.forgeai.task.TaskDtos.DesignRefs;
import com.forgeai.task.TaskDtos.MoveTask;
import com.forgeai.task.TaskDtos.SaveTask;
import com.forgeai.task.TaskDtos.TaskCounts;
import com.forgeai.task.TaskDtos.TaskEventResponse;
import com.forgeai.task.TaskDtos.TaskResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Backlog use cases (except AI generation, see {@link TaskGenerationService}). Every method is
 * scoped to a project the caller owns; every change is logged as a {@link TaskEvent}.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final TaskBacklogRepository backlogs;
    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DatabaseDesignService databases;
    private final ApiDesignService apis;
    private final JsonMapper jsonMapper;

    TaskService(
            TaskRepository tasks,
            TaskEventRepository events,
            TaskBacklogRepository backlogs,
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DatabaseDesignService databases,
            ApiDesignService apis,
            JsonMapper jsonMapper) {
        this.tasks = tasks;
        this.events = events;
        this.backlogs = backlogs;
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.databases = databases;
        this.apis = apis;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public BacklogOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return overview(projectId);
    }

    @Transactional
    public BacklogOverview create(UUID ownerId, UUID projectId, SaveTask request) {
        projects.get(ownerId, projectId);
        List<Task> all = tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId);
        TaskContent content = TaskRules.tidy(request);
        check(content, null, all, inputs(projectId), null);
        Task task = tasks.saveAndFlush(new Task(projectId, tasks.maxSeqNumber(projectId) + 1, all.size(), TaskSource.USER,
                content, write(content.details())));
        events.save(TaskEvent.of(task, TaskEventType.CREATED));
        return overview(projectId);
    }

    @Transactional
    public BacklogOverview update(UUID ownerId, UUID projectId, UUID taskId, SaveTask request) {
        Task task = findOwned(ownerId, projectId, taskId);
        if (request.version() == null) throw new BadRequestException(List.of("The version of the task is required."));
        checkVersion(task, request.version());
        TaskContent content = TaskRules.tidy(request);
        check(content, task, tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId), inputs(projectId), details(task));
        task.edit(content, write(content.details()));
        tasks.saveAndFlush(task);
        events.save(TaskEvent.of(task, TaskEventType.EDITED));
        return overview(projectId);
    }

    /** Moves a task on the board. Starting or finishing it needs every task it depends on done. */
    @Transactional
    public BacklogOverview changeStatus(UUID ownerId, UUID projectId, UUID taskId, ChangeTaskStatus request) {
        Task task = findOwned(ownerId, projectId, taskId);
        checkVersion(task, request.version());
        TaskStatus from = task.getStatus();
        if (from == request.status()) return overview(projectId);
        if (request.status() != TaskStatus.TODO) {
            List<String> waiting = blockedBy(details(task), byCode(tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId)));
            if (!waiting.isEmpty()) {
                throw new ConflictException(task.code() + " is waiting for " + String.join(", ", waiting) + " to be done.");
            }
        }
        task.moveTo(request.status());
        tasks.saveAndFlush(task);
        events.save(TaskEvent.statusChange(task, from, request.status()));
        return overview(projectId);
    }

    /** Moves a task to another place in the backlog order. */
    @Transactional
    public BacklogOverview move(UUID ownerId, UUID projectId, UUID taskId, MoveTask request) {
        Task task = findOwned(ownerId, projectId, taskId);
        checkVersion(task, request.version());
        List<Task> ordered = new ArrayList<>(tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId));
        ordered.removeIf(t -> t.getId().equals(task.getId()));
        ordered.add(Math.min(request.position(), ordered.size()), task);
        renumber(ordered);
        return overview(projectId);
    }

    /** Deletes a task, unless another task still depends on it. */
    @Transactional
    public BacklogOverview delete(UUID ownerId, UUID projectId, UUID taskId) {
        Task task = findOwned(ownerId, projectId, taskId);
        List<Task> all = tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId);
        List<String> dependents = all.stream()
                .filter(t -> details(t).dependsOn().contains(task.code()))
                .map(Task::code)
                .toList();
        if (!dependents.isEmpty()) {
            throw new ConflictException(String.join(", ", dependents) + (dependents.size() == 1 ? " depends" : " depend")
                    + " on " + task.code() + ". Remove the dependency first.");
        }
        tasks.delete(task);
        tasks.flush();
        renumber(all.stream().filter(t -> !t.getId().equals(task.getId())).toList());
        return overview(projectId);
    }

    @Transactional(readOnly = true)
    public List<TaskEventResponse> events(UUID ownerId, UUID projectId, UUID taskId) {
        Task task = findOwned(ownerId, projectId, taskId);
        return events.findAllByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                .map(e -> new TaskEventResponse(e.getType(), e.getFromStatus(), e.getToStatus(), e.getCreatedAt()))
                .toList();
    }

    // --- Used by code generation ---------------------------------------------------

    /**
     * A task as code generation sees it.
     *
     * @param blockedBy    tasks it depends on that are not done yet
     * @param dependencies the tasks it depends on, as "T-002 Title"
     */
    public record CodeTask(
            UUID id,
            String code,
            String title,
            String description,
            TaskType type,
            TaskStatus status,
            TaskDetails details,
            List<String> blockedBy,
            List<String> dependencies) {
    }

    @Transactional(readOnly = true)
    public List<CodeTask> codeTasks(UUID projectId) {
        List<Task> all = tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId);
        Map<String, Task> byCode = byCode(all);
        return all.stream().map(t -> {
            TaskDetails d = details(t);
            return new CodeTask(t.getId(), t.code(), t.getTitle(), t.getDescription(), t.getType(), t.getStatus(), d,
                    blockedBy(d, byCode),
                    d.dependsOn().stream()
                            .filter(byCode::containsKey)
                            .map(c -> c + " " + byCode.get(c).getTitle())
                            .toList());
        }).toList();
    }

    /** Why later stages cannot build on the design yet (something not approved); null when they can. */
    @Transactional(readOnly = true)
    public String designBlocker(UUID projectId) {
        return inputs(projectId).blocker();
    }

    /** Moves a task that is still to do to in progress (code is being written for it). */
    @Transactional
    public void markStarted(UUID projectId, UUID taskId) {
        tasks.findByIdAndProjectId(taskId, projectId)
                .filter(t -> t.getStatus() == TaskStatus.TODO)
                .ifPresent(task -> {
                    task.moveTo(TaskStatus.IN_PROGRESS);
                    tasks.saveAndFlush(task);
                    events.save(TaskEvent.statusChange(task, TaskStatus.TODO, TaskStatus.IN_PROGRESS));
                });
    }

    // --- Used by TaskGenerationService ------------------------------------------

    /** Everything generation reads, captured at one moment. */
    record Inputs(
            RequirementSnapshot requirements,
            ArchitectureSnapshot architecture,
            DatabaseSnapshot database,
            ApiSnapshot api) {

        String fingerprint() {
            return Fingerprints.sha256(
                    api == null ? "" : api.fingerprint(),
                    database == null ? "" : database.fingerprint(),
                    architecture == null ? "" : architecture.fingerprint(),
                    requirements.approvedFingerprint());
        }

        /** What tasks may link to: approved requirements (or all, for edits) and the current design. */
        TaskRules.Allowed allowed(boolean approvedRequirementsOnly) {
            Collection<RequirementResponse> reqs = approvedRequirementsOnly ? requirements.approved() : requirements.all();
            return new TaskRules.Allowed(
                    reqs.stream().map(RequirementResponse::code).collect(Collectors.toSet()),
                    database == null ? Set.of() : database.content().entities().stream()
                            .map(DatabaseDesignContent.Entity::name).collect(Collectors.toSet()),
                    api == null ? Set.of() : endpoints(api.content()),
                    architecture == null ? Set.of() : architecture.content().components().stream()
                            .map(ArchitectureContent.Component::name).collect(Collectors.toSet()));
        }

        /** Why generating is not possible right now; null when it is. */
        String blocker() {
            if (!requirements.allApproved()) return "Approve all requirements first.";
            if (architecture == null || architecture.status() != ReviewStatus.APPROVED) return "Approve the architecture first.";
            if (database == null || database.status() != ReviewStatus.APPROVED) return "Approve the database design first.";
            if (api == null) return "Generate and approve the API design first.";
            if (api.status() != ReviewStatus.APPROVED) return "Approve the API design first.";
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Inputs inputs(UUID projectId) {
        return new Inputs(
                requirements.snapshot(projectId),
                architectures.snapshot(projectId).orElse(null),
                databases.snapshot(projectId).orElse(null),
                apis.snapshot(projectId).orElse(null));
    }

    /** Tasks regeneration keeps: everything except untouched AI tasks that are still to do. */
    @Transactional(readOnly = true)
    public List<Task> keptOnRegeneration(UUID projectId) {
        return tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId).stream()
                .filter(t -> !replaceable(t))
                .toList();
    }

    @Transactional(readOnly = true)
    public int nextSeqNumber(UUID projectId) {
        return tasks.maxSeqNumber(projectId) + 1;
    }

    /**
     * Replaces untouched AI tasks with newly generated ones, appended after the kept tasks.
     * Dependencies of kept tasks on replaced ones are removed.
     */
    @Transactional
    public void replaceGenerated(UUID projectId, List<TaskAssistant.NewTask> generated, String fingerprint, String model) {
        List<Task> all = tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId);
        List<Task> kept = all.stream().filter(t -> !replaceable(t)).toList();
        Set<String> removed = all.stream().filter(TaskService::replaceable).map(Task::code).collect(Collectors.toSet());
        tasks.deleteAll(all.stream().filter(TaskService::replaceable).toList());
        tasks.flush();

        for (Task task : kept) {
            TaskDetails d = details(task);
            if (d.dependsOn().stream().anyMatch(removed::contains)) {
                task.replaceDetails(write(d.withDependsOn(d.dependsOn().stream().filter(c -> !removed.contains(c)).toList())));
            }
        }
        List<Task> ordered = new ArrayList<>(kept);
        for (TaskAssistant.NewTask item : generated) {
            Task task = tasks.save(new Task(projectId, item.seqNumber(), 0, TaskSource.AI, item.content(), write(item.content().details())));
            ordered.add(task);
        }
        renumber(ordered);
        for (int i = kept.size(); i < ordered.size(); i++) {
            events.save(TaskEvent.of(ordered.get(i), TaskEventType.CREATED));
        }

        TaskBacklog backlog = backlogs.findById(projectId).orElseGet(() -> new TaskBacklog(projectId));
        backlog.generated(fingerprint, model);
        backlogs.save(backlog);
    }

    public BacklogOverview overview(UUID projectId) {
        Inputs inputs = inputs(projectId);
        List<Task> all = tasks.findAllByProjectIdOrderByPositionAscSeqNumberAsc(projectId);
        Map<String, Task> byCode = byCode(all);
        Map<String, TaskDetails> details = new HashMap<>();
        all.forEach(t -> details.put(t.code(), details(t)));

        int todo = 0;
        int inProgress = 0;
        int done = 0;
        int points = 0;
        int pointsDone = 0;
        for (Task t : all) {
            switch (t.getStatus()) {
                case TODO -> todo++;
                case IN_PROGRESS -> inProgress++;
                case DONE -> done++;
            }
            int estimate = t.getEstimate() == null ? 0 : t.getEstimate();
            points += estimate;
            if (t.getStatus() == TaskStatus.DONE) pointsDone += estimate;
        }

        TaskRules.Allowed current = inputs.allowed(true);
        Map<String, String> functional = new LinkedHashMap<>();
        inputs.requirements().approved().stream()
                .filter(r -> r.type() == RequirementType.FUNCTIONAL)
                .forEach(r -> functional.put(r.code(), r.title()));
        Optional<TaskBacklog> backlog = backlogs.findById(projectId);
        String blocker = inputs.blocker();

        return new BacklogOverview(
                all.stream().map(t -> response(t, details.get(t.code()), byCode)).toList(),
                new TaskCounts(todo, inProgress, done, all.size(), points, pointsDone),
                new DesignRefs(
                        inputs.requirements().approved().stream()
                                .map(r -> new RequirementRef(r.code(), r.type(), r.title(), r.priority())).toList(),
                        new TreeSet<>(current.entities()).stream().toList(),
                        inputs.api() == null ? List.of() : orderedEndpoints(inputs.api().content()),
                        inputs.architecture() == null ? List.of()
                                : inputs.architecture().content().components().stream().map(ArchitectureContent.Component::name).toList()),
                blocker == null,
                blocker,
                backlog.map(TaskBacklog::getGeneratedAt).orElse(null),
                backlog.map(TaskBacklog::getModel).orElse(null),
                backlog.isPresent() && inputs.api() != null && !backlog.get().getInputFingerprint().equals(inputs.fingerprint()),
                TaskRules.findings(
                        all.stream().map(t -> new TaskRules.Item(t.code(), t.getPosition(), details.get(t.code()))).toList(),
                        current,
                        functional));
    }

    // --- Helpers -----------------------------------------------------------------

    static Set<String> endpoints(ApiDesignContent api) {
        return new java.util.HashSet<>(orderedEndpoints(api));
    }

    static List<String> orderedEndpoints(ApiDesignContent api) {
        return api.endpoints().stream().map(e -> e.method() + " " + e.path()).toList();
    }

    private static boolean replaceable(Task task) {
        return task.getSource() == TaskSource.AI && !task.isEdited() && task.getStatus() == TaskStatus.TODO;
    }

    private void check(TaskContent content, Task self, List<Task> all, Inputs inputs, TaskDetails previous) {
        Map<String, List<String>> dependencies = new HashMap<>();
        for (Task t : all) {
            if (self != null && t.getId().equals(self.getId())) continue;
            dependencies.put(t.code(), details(t).dependsOn());
        }
        List<String> problems = TaskRules.problems(content, self == null ? null : self.code(), dependencies,
                inputs.allowed(false), previous);
        if (!problems.isEmpty()) throw new BadRequestException(problems);
    }

    private void renumber(List<Task> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getPosition() != i) ordered.get(i).placeAt(i);
        }
        tasks.saveAllAndFlush(ordered);
    }

    private TaskResponse response(Task t, TaskDetails d, Map<String, Task> byCode) {
        return new TaskResponse(t.getId(), t.code(), t.getPosition(), t.getTitle(), t.getDescription(), t.getType(),
                t.getEstimate(), t.getStatus(), t.getSource(), t.isEdited(), d.acceptanceCriteria(), d.dependsOn(),
                d.requirements(), d.entities(), d.endpoints(), d.components(), blockedBy(d, byCode), t.getVersion(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private static List<String> blockedBy(TaskDetails details, Map<String, Task> byCode) {
        return details.dependsOn().stream()
                .filter(code -> byCode.containsKey(code) && byCode.get(code).getStatus() != TaskStatus.DONE)
                .toList();
    }

    private static Map<String, Task> byCode(List<Task> all) {
        Map<String, Task> map = new HashMap<>();
        all.forEach(t -> map.put(t.code(), t));
        return map;
    }

    public TaskDetails details(Task task) {
        return jsonMapper.readValue(task.getDetailsJson(), TaskDetails.class);
    }

    private String write(TaskDetails details) {
        return jsonMapper.writeValueAsString(details);
    }

    private static void checkVersion(Task task, int expectedVersion) {
        if (task.getVersion() != expectedVersion) {
            throw new ConflictException(task.code() + " was changed in another tab or window. Reload to see the latest version.");
        }
    }

    private Task findOwned(UUID ownerId, UUID projectId, UUID taskId) {
        projects.get(ownerId, projectId);
        return tasks.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
    }
}
