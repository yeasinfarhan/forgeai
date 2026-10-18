package com.forgeai.code;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.forgeai.architecture.ArchitectureService;
import com.forgeai.code.CodeDtos.ChangesetResponse;
import com.forgeai.common.concurrent.GenerationGuard;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.design.database.DatabaseDesignService;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.task.TaskGenerationService;
import com.forgeai.task.TaskService;
import com.forgeai.task.TaskService.CodeTask;

/**
 * Generates the code for one task. Not transactional: the AI call can take minutes, so reads and
 * the final write happen in short transactions in {@link CodeService}.
 *
 * <p>A task's code can be generated once everything it depends on is done, so the code it builds
 * on is already accepted and part of the context.
 */
@Service
public class CodeGenerationService {

    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DatabaseDesignService databases;
    private final ApiDesignService apis;
    private final TaskService tasks;
    private final CodeService code;
    private final CodeAssistant assistant;
    private final GenerationGuard generationGuard;

    CodeGenerationService(
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DatabaseDesignService databases,
            ApiDesignService apis,
            TaskService tasks,
            CodeService code,
            CodeAssistant assistant,
            GenerationGuard generationGuard) {
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.databases = databases;
        this.apis = apis;
        this.tasks = tasks;
        this.code = code;
        this.assistant = assistant;
        this.generationGuard = generationGuard;
    }

    public ChangesetResponse generate(UUID ownerId, UUID projectId, UUID taskId) {
        return generationGuard.run(projectId, "code/" + taskId, "code for this task", () -> doGenerate(ownerId, projectId, taskId));
    }

    private ChangesetResponse doGenerate(UUID ownerId, UUID projectId, UUID taskId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        var codeTasks = tasks.codeTasks(projectId);
        String blocker = code.blocker(projectId, codeTasks);
        if (blocker != null) throw new ConflictException(blocker);
        CodeTask task = codeTasks.stream().filter(t -> t.id().equals(taskId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        if (!task.blockedBy().isEmpty()) {
            throw new ConflictException(task.code() + " is waiting for " + String.join(", ", task.blockedBy())
                    + " to be done. Finish those first, so their code is accepted and can be built on.");
        }

        CodeAssistant.Design design = new CodeAssistant.Design(
                project.name(),
                architectures.snapshot(projectId).orElseThrow().content().toPromptText(),
                ApiDesignService.renderDatabase(databases.snapshot(projectId).orElseThrow().content()),
                TaskGenerationService.renderApi(apis.snapshot(projectId).orElseThrow().content()));
        Map<String, String> requirementTitles = requirements.snapshot(projectId).approved().stream()
                .collect(Collectors.toMap(RequirementResponse::code, RequirementResponse::title));

        CodeAssistant.Generated generated = assistant.generate(design, task, requirementTitles, code.workspace(projectId));
        CodeChangeset changeset;
        try {
            changeset = code.store(projectId, task, generated);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("The task changed while its code was being generated. Reload and try again.");
        }
        return code.response(projectId, changeset.getId());
    }
}
