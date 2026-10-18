package com.forgeai.task;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.forgeai.common.exception.ConflictException;
import com.forgeai.design.api.ApiDesignContent;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.task.TaskDtos.BacklogOverview;

/**
 * Generates backlog tasks from the approved design. Not transactional: the AI call can take a
 * while, so reads and the final write happen in short transactions in {@link TaskService}.
 *
 * <p>Regeneration keeps every task that was started, edited or written by hand, tells the AI
 * about them, and replaces only untouched AI tasks that are still to do.
 */
@Service
public class TaskGenerationService {

    private final ProjectService projects;
    private final TaskService tasks;
    private final TaskAssistant assistant;

    TaskGenerationService(ProjectService projects, TaskService tasks, TaskAssistant assistant) {
        this.projects = projects;
        this.tasks = tasks;
        this.assistant = assistant;
    }

    public BacklogOverview generate(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        TaskService.Inputs inputs = tasks.inputs(projectId);
        String blocker = inputs.blocker();
        if (blocker != null) throw new ConflictException(blocker);

        List<Task> kept = tasks.keptOnRegeneration(projectId);
        Map<String, List<String>> existing = new LinkedHashMap<>();
        Map<String, Task> byCode = new HashMap<>();
        List<TaskRules.Item> items = kept.stream().map(t -> {
            TaskDetails details = tasks.details(t);
            existing.put(t.code(), details.dependsOn());
            byCode.put(t.code(), t);
            return new TaskRules.Item(t.code(), t.getPosition(), details);
        }).toList();

        TaskAssistant.Generated generated = assistant.generate(
                new TaskAssistant.Inputs(
                        project.name(),
                        inputs.architecture().content().toPromptText(),
                        ApiDesignService.renderDatabase(inputs.database().content()),
                        renderApi(inputs.api().content()),
                        inputs.requirements().approvedAsPromptText(),
                        TaskAssistant.renderExisting(items, byCode)),
                inputs.allowed(true),
                existing,
                tasks.nextSeqNumber(projectId));
        try {
            tasks.replaceGenerated(projectId, generated.tasks(), inputs.fingerprint(), generated.model());
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The backlog changed while generating. Reload and try again.");
        }
        return tasks.overview(ownerId, projectId);
    }

    /** Endpoints as "METHOD /path" lines (the form tasks link to), with what they do. */
    static String renderApi(ApiDesignContent api) {
        StringBuilder text = new StringBuilder("Base path: ").append(api.basePath()).append('\n');
        text.append("Authentication: ").append(api.authentication()).append('\n');
        for (ApiDesignContent.Endpoint e : api.endpoints()) {
            text.append("- ").append(e.method()).append(' ').append(e.path()).append(": ").append(e.summary());
            if (e.entity() != null) text.append(" (entity ").append(e.entity()).append(')');
            if (!e.requirements().isEmpty()) text.append(" [").append(String.join(", ", e.requirements())).append(']');
            text.append('\n');
        }
        return text.toString().strip();
    }
}
