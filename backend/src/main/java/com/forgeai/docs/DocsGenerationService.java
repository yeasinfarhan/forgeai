package com.forgeai.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.forgeai.common.concurrent.GenerationGuard;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.docs.DocsDtos.DocumentResponse;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.task.TaskGenerationService;

/**
 * Writes the README or the developer guide with the AI. Not transactional: the AI call can take
 * minutes, so reads and the final write happen in short transactions in {@link DocsService}.
 */
@Service
public class DocsGenerationService {

    private final ProjectService projects;
    private final DocsService docs;
    private final DocsAssistant assistant;
    private final GenerationGuard generationGuard;

    DocsGenerationService(ProjectService projects, DocsService docs, DocsAssistant assistant, GenerationGuard generationGuard) {
        this.projects = projects;
        this.docs = docs;
        this.assistant = assistant;
        this.generationGuard = generationGuard;
    }

    public DocumentResponse generate(UUID ownerId, UUID projectId, DocumentKind kind) {
        return generationGuard.run(projectId, "docs/" + kind.name(), DocsService.label(kind), () -> doGenerate(ownerId, projectId, kind));
    }

    private DocumentResponse doGenerate(UUID ownerId, UUID projectId, DocumentKind kind) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DocsInputs inputs = docs.inputs(project);
        if (inputs.blocker() != null) throw new ConflictException(inputs.blocker());

        Map<String, String> design = Map.of(
                "projectName", project.name(),
                "projectDescription", project.description() == null || project.description().isBlank() ? "(none)" : project.description(),
                "requirements", inputs.requirements().approvedAsPromptText(),
                "architecture", inputs.architecture().content().toPromptText(),
                "database", ApiDesignService.renderDatabase(inputs.database().content()),
                "api", TaskGenerationService.renderApi(inputs.api().content()));
        DocsAssistant.Written written = assistant.write(kind, design, otherDocuments(kind, inputs), inputs.files());
        try {
            return docs.store(project, kind, written, inputs.fingerprint());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("The " + DocsService.label(kind) + " was written twice at the same time. Reload and try again.");
        }
    }

    /** The other documents of the package, so the one being written can link to them. */
    static List<String> otherDocuments(DocumentKind kind, DocsInputs inputs) {
        List<String> paths = new ArrayList<>();
        for (DocumentKind other : DocumentKind.values()) {
            if (other != kind) paths.add(other.path());
        }
        paths.addAll(List.of(ProjectPackage.REQUIREMENTS, ProjectPackage.ARCHITECTURE, ProjectPackage.DATA_MODEL, ProjectPackage.SQL,
                ProjectPackage.API_REFERENCE, ProjectPackage.OPENAPI));
        if (!inputs.tasks().isEmpty()) paths.add(ProjectPackage.BACKLOG);
        return paths;
    }
}
