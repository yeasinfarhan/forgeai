package com.forgeai.docs;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.architecture.ArchitectureService;
import com.forgeai.code.CodeService;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.design.api.ApiDesignService;
import com.forgeai.design.database.DatabaseDesignService;
import com.forgeai.docs.DocsDtos.DocsOverview;
import com.forgeai.docs.DocsDtos.DocumentResponse;
import com.forgeai.docs.DocsDtos.DocumentSummary;
import com.forgeai.docs.DocsDtos.PackageEntry;
import com.forgeai.docs.DocsDtos.PackageFile;
import com.forgeai.docs.DocsDtos.UpdateDocument;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementService;
import com.forgeai.task.TaskService;

/**
 * Project documentation: the AI-written README and developer guide (read, edited by hand) and the
 * full project package. Writing a document with the AI is done by {@link DocsGenerationService}.
 */
@Service
public class DocsService {

    private final ProjectDocumentRepository documents;
    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DatabaseDesignService databases;
    private final ApiDesignService apis;
    private final TaskService tasks;
    private final CodeService code;
    private final ProjectPackage projectPackage;

    DocsService(
            ProjectDocumentRepository documents,
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DatabaseDesignService databases,
            ApiDesignService apis,
            TaskService tasks,
            CodeService code,
            ProjectPackage projectPackage) {
        this.documents = documents;
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.databases = databases;
        this.apis = apis;
        this.tasks = tasks;
        this.code = code;
        this.projectPackage = projectPackage;
    }

    @Transactional(readOnly = true)
    public DocsOverview overview(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DocsInputs inputs = inputs(project);
        List<ProjectDocument> stored = documents.findAllByProjectId(projectId);
        Map<DocumentKind, ProjectDocument> byKind = stored.stream()
                .collect(Collectors.toMap(ProjectDocument::getKind, Function.identity()));
        String fingerprint = inputs.fingerprint();
        List<DocumentSummary> summaries = java.util.Arrays.stream(DocumentKind.values())
                .map(kind -> {
                    ProjectDocument d = byKind.get(kind);
                    return d == null
                            ? new DocumentSummary(kind, kind.title(), kind.path(), false, false, false, null, null, null)
                            : new DocumentSummary(kind, kind.title(), kind.path(), true, d.isEdited(),
                                    !d.getInputFingerprint().equals(fingerprint), d.getModel(), d.getGeneratedAt(), d.getUpdatedAt());
                })
                .toList();
        List<PackageEntry> entries = inputs.blocker() != null ? List.of()
                : projectPackage.files(inputs, stored).stream()
                        .map(f -> new PackageEntry(f.path(), f.source(),
                                f.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, f.replacesCode()))
                        .toList();
        return new DocsOverview(inputs.blocker() == null, inputs.blocker(), summaries, ProjectPackage.fileName(project.name()), entries);
    }

    @Transactional(readOnly = true)
    public DocumentResponse document(UUID ownerId, UUID projectId, DocumentKind kind) {
        ProjectResponse project = projects.get(ownerId, projectId);
        return response(find(projectId, kind), inputs(project).fingerprint());
    }

    /** Saves a person's edit of a document. */
    @Transactional
    public DocumentResponse update(UUID ownerId, UUID projectId, DocumentKind kind, UpdateDocument request) {
        ProjectResponse project = projects.get(ownerId, projectId);
        ProjectDocument document = find(projectId, kind);
        if (document.getVersion() != request.version().intValue()) {
            throw new ConflictException("The " + label(kind) + " was changed in another tab or window. Reload to see the latest version.");
        }
        document.edit(request.content().strip() + "\n");
        documents.saveAndFlush(document);
        return response(document, inputs(project).fingerprint());
    }

    /** One file of the project package, to preview it. */
    @Transactional(readOnly = true)
    public PackageFile packageFile(UUID ownerId, UUID projectId, String path) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DocsInputs inputs = requireComplete(inputs(project));
        String wanted = path == null ? "" : path.strip();
        return projectPackage.files(inputs, documents.findAllByProjectId(projectId)).stream()
                .filter(f -> f.path().equals(wanted))
                .findFirst()
                .map(f -> new PackageFile(f.path(), f.source(), f.content()))
                .orElseThrow(() -> new ResourceNotFoundException("File not found in the package."));
    }

    public record PackageExport(String fileName, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public PackageExport export(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DocsInputs inputs = requireComplete(inputs(project));
        List<ProjectPackage.File> files = projectPackage.files(inputs, documents.findAllByProjectId(projectId));
        return new PackageExport(ProjectPackage.fileName(project.name()), ProjectPackage.zip(project.name(), files));
    }

    // --- Used by DocsGenerationService ---------------------------------------------

    @Transactional(readOnly = true)
    public DocsInputs inputs(ProjectResponse project) {
        UUID projectId = project.id();
        return new DocsInputs(
                project,
                tasks.designBlocker(projectId),
                requirements.snapshot(projectId),
                architectures.snapshot(projectId).orElse(null),
                databases.snapshot(projectId).orElse(null),
                apis.snapshot(projectId).orElse(null),
                tasks.codeTasks(projectId),
                code.sourceFiles(projectId));
    }

    /** Stores a newly written document, replacing the previous one (and any edits of it). */
    @Transactional
    public DocumentResponse store(ProjectResponse project, DocumentKind kind, DocsAssistant.Written written, String fingerprint) {
        ProjectDocument document = documents.findByProjectIdAndKind(project.id(), kind)
                .orElseGet(() -> new ProjectDocument(project.id(), kind));
        document.generated(written.content(), written.model(), fingerprint);
        documents.saveAndFlush(document);
        return response(document, inputs(project).fingerprint());
    }

    static String label(DocumentKind kind) {
        return kind == DocumentKind.README ? "README" : "developer guide";
    }

    // --- Helpers -------------------------------------------------------------------

    private static DocsInputs requireComplete(DocsInputs inputs) {
        if (inputs.blocker() != null) throw new ConflictException(inputs.blocker());
        return inputs;
    }

    private ProjectDocument find(UUID projectId, DocumentKind kind) {
        return documents.findByProjectIdAndKind(projectId, kind)
                .orElseThrow(() -> new ResourceNotFoundException("The " + label(kind) + " has not been written yet."));
    }

    private static DocumentResponse response(ProjectDocument d, String currentFingerprint) {
        return new DocumentResponse(d.getKind(), d.getKind().title(), d.getKind().path(), d.getContent(), d.isEdited(),
                !d.getInputFingerprint().equals(currentFingerprint), d.getModel(), d.getGeneratedAt(), d.getUpdatedAt(),
                d.getVersion());
    }
}
