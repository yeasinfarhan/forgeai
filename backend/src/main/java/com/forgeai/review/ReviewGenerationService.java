package com.forgeai.review;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.forgeai.architecture.ArchitectureService;
import com.forgeai.code.CodeService;
import com.forgeai.code.CodeService.SourceFile;
import com.forgeai.common.concurrent.GenerationGuard;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.design.database.DatabaseDesignService;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;
import com.forgeai.review.ReviewDtos.ReviewResponse;
import com.forgeai.task.TaskGenerationService;
import com.forgeai.task.TaskService;
import com.forgeai.task.TaskService.CodeTask;

/**
 * Reviews the accepted code with the AI. Not transactional: the AI call can take minutes, so reads
 * and the final write happen in short transactions.
 */
@Service
public class ReviewGenerationService {

    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DatabaseDesignService databases;
    private final ApiDesignService apis;
    private final TaskService tasks;
    private final CodeService code;
    private final ReviewService reviews;
    private final ReviewAssistant assistant;
    private final GenerationGuard generationGuard;

    ReviewGenerationService(
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DatabaseDesignService databases,
            ApiDesignService apis,
            TaskService tasks,
            CodeService code,
            ReviewService reviews,
            ReviewAssistant assistant,
            GenerationGuard generationGuard) {
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.databases = databases;
        this.apis = apis;
        this.tasks = tasks;
        this.code = code;
        this.reviews = reviews;
        this.assistant = assistant;
        this.generationGuard = generationGuard;
    }

    public ReviewResponse generate(UUID ownerId, UUID projectId) {
        return generationGuard.run(projectId, "review", "code review", () -> doGenerate(ownerId, projectId));
    }

    private ReviewResponse doGenerate(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        List<SourceFile> files = code.sourceFiles(projectId);
        String blocker = reviews.blocker(projectId, files);
        if (blocker != null) throw new ConflictException(blocker);

        RequirementSnapshot snapshot = requirements.snapshot(projectId);
        Set<String> requirementCodes = snapshot.approved().stream().map(RequirementResponse::code).collect(Collectors.toSet());
        ReviewAssistant.Design design = new ReviewAssistant.Design(
                project.name(),
                snapshot.approvedAsPromptText(),
                architectures.snapshot(projectId).orElseThrow().content().toPromptText(),
                ApiDesignService.renderDatabase(databases.snapshot(projectId).orElseThrow().content()),
                TaskGenerationService.renderApi(apis.snapshot(projectId).orElseThrow().content()),
                renderTasks(tasks.codeTasks(projectId)));

        ReviewAssistant.Result result = assistant.review(design, files, requirementCodes);
        return reviews.store(projectId, result, ReviewService.fingerprint(files));
    }

    /** The backlog in one line per task, so the review does not report work a task still to do will cover. */
    static String renderTasks(List<CodeTask> list) {
        if (list.isEmpty()) return "(none)";
        return list.stream()
                .map(t -> t.code() + " [" + t.status() + "] " + t.title())
                .collect(Collectors.joining("\n"));
    }
}
