package com.forgeai.design.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.common.concurrent.GenerationGuard;
import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.architecture.ArchitectureService;
import com.forgeai.architecture.ArchitectureSnapshot;
import com.forgeai.common.exception.BadRequestException;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.design.DesignDocument;
import com.forgeai.design.DesignDocumentStore;
import com.forgeai.design.DesignDtos.ChangeDesignStatus;
import com.forgeai.design.DesignDtos.DesignState;
import com.forgeai.design.DesignDtos.RevisionDetail;
import com.forgeai.design.DesignDtos.RevisionSummary;
import com.forgeai.design.DesignExport;
import com.forgeai.design.DesignKind;
import com.forgeai.design.DesignRevision;
import com.forgeai.design.api.ApiDtos.ApiDesignOverview;
import com.forgeai.design.api.ApiDtos.UpdateApiDesign;
import com.forgeai.design.consistency.ConsistencyService;
import com.forgeai.design.database.DatabaseDesignContent;
import com.forgeai.design.database.DatabaseDesignService;
import com.forgeai.design.database.DatabaseDtos.DatabaseSnapshot;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;

import tools.jackson.databind.json.JsonMapper;

/**
 * API design use cases. Built from the approved data model, architecture and requirements;
 * generation calls the AI outside any transaction, storage and workflow go through
 * {@link DesignDocumentStore}.
 */
@Service
public class ApiDesignService {

    private static final DesignKind KIND = DesignKind.API;

    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DatabaseDesignService databases;
    private final DesignDocumentStore store;
    private final ConsistencyService consistency;
    private final ApiAssistant assistant;
    private final JsonMapper jsonMapper;

    private final GenerationGuard generationGuard;

    ApiDesignService(
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DatabaseDesignService databases,
            DesignDocumentStore store,
            ConsistencyService consistency,
            ApiAssistant assistant,
            JsonMapper jsonMapper,
            GenerationGuard generationGuard) {
        this.generationGuard = generationGuard;
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.databases = databases;
        this.store = store;
        this.consistency = consistency;
        this.assistant = assistant;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ApiDesignOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return overview(projectId);
    }

    /** Generates (or regenerates, while in draft) the API. Calls the AI; can take up to a minute. */
    public ApiDesignOverview generate(UUID ownerId, UUID projectId) {
        return generationGuard.run(projectId, "api-design", "API design", () -> doGenerate(ownerId, projectId));
    }

    private ApiDesignOverview doGenerate(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        Inputs inputs = inputs(projectId);
        Optional<DesignDocument> existing = store.find(projectId, KIND);
        String blocker = blocker(inputs, existing);
        if (blocker != null) throw new ConflictException(blocker);

        DatabaseDesignContent database = inputs.database().content();
        Set<String> codes = inputs.requirements().approved().stream().map(RequirementResponse::code).collect(Collectors.toSet());
        Set<String> entities = database.entities().stream().map(DatabaseDesignContent.Entity::name).collect(Collectors.toSet());
        ApiAssistant.Generated generated = assistant.generate(project.name(),
                inputs.architecture().content().toPromptText(),
                renderDatabase(database),
                inputs.requirements().approvedAsPromptText(),
                entities,
                codes);
        try {
            store.storeGenerated(projectId, KIND, existing.map(DesignDocument::getVersion).orElse(null),
                    jsonMapper.writeValueAsString(generated.content()), generated.model(), inputs.fingerprint());
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The API design changed while generating. Reload and try again.");
        }
        return overview(ownerId, projectId);
    }

    @Transactional
    public ApiDesignOverview update(UUID ownerId, UUID projectId, UpdateApiDesign request) {
        projects.get(ownerId, projectId);
        ApiDesignContent content = ApiRules.tidy(request.content());
        Set<String> codes = requirements.snapshot(projectId).all().stream().map(RequirementResponse::code).collect(Collectors.toSet());
        List<String> problems = ApiRules.problems(content, codes);
        if (!problems.isEmpty()) throw new BadRequestException(problems);
        store.edit(projectId, KIND, request.version(), jsonMapper.writeValueAsString(content));
        return overview(projectId);
    }

    @Transactional
    public ApiDesignOverview changeStatus(UUID ownerId, UUID projectId, ChangeDesignStatus request) {
        projects.get(ownerId, projectId);
        store.changeStatus(projectId, KIND, request.version(), request.status(), () -> {
            Inputs inputs = inputs(projectId);
            if (!inputs.requirements().allApproved()) return Optional.of("Approve all requirements before approving the API design.");
            if (inputs.database() == null || inputs.database().status() != ReviewStatus.APPROVED) {
                return Optional.of("Approve the database design before approving the API design.");
            }
            return Optional.empty();
        });
        return overview(projectId);
    }

    @Transactional(readOnly = true)
    public List<RevisionSummary> history(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return store.history(projectId, KIND);
    }

    @Transactional(readOnly = true)
    public RevisionDetail<ApiDesignContent> revision(UUID ownerId, UUID projectId, int revision) {
        projects.get(ownerId, projectId);
        DesignRevision r = store.revision(projectId, KIND, revision);
        return new RevisionDetail<>(r.getRevision(), r.getChangeType(), r.getStatus(), r.getModel(), r.getCreatedAt(), read(r.getContentJson()));
    }

    /** The current API as an OpenAPI 3.1 document. */
    @Transactional(readOnly = true)
    public DesignExport exportOpenApi(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DesignDocument document = store.find(projectId, KIND)
                .orElseThrow(() -> new ConflictException("Generate the API design first."));
        Map<String, Object> openApi = OpenApiExporter.export(read(document.getContentJson()), project.name() + " API",
                "revision " + document.getRevision() + " (" + document.getStatus().label() + ")");
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openApi);
        return new DesignExport(DesignExport.slug(project.name()) + "-openapi.json", "application/json", json + "\n");
    }

    // --- Helpers -----------------------------------------------------------------

    /** What the API is built from, and their combined fingerprint. */
    private record Inputs(DatabaseSnapshot database, ArchitectureSnapshot architecture, RequirementSnapshot requirements) {

        String fingerprint() {
            return Fingerprints.sha256(
                    database == null ? "" : database.fingerprint(),
                    architecture == null ? "" : architecture.fingerprint(),
                    requirements.approvedFingerprint());
        }
    }

    private Inputs inputs(UUID projectId) {
        return new Inputs(databases.snapshot(projectId).orElse(null), architectures.snapshot(projectId).orElse(null),
                requirements.snapshot(projectId));
    }

    private static String blocker(Inputs inputs, Optional<DesignDocument> existing) {
        RequirementSnapshot requirements = inputs.requirements();
        if (!requirements.allApproved()) return "Approve all requirements first.";
        if (inputs.architecture() == null || inputs.architecture().status() != ReviewStatus.APPROVED) {
            return "Approve the architecture first.";
        }
        if (inputs.database() == null) return "Generate and approve the database design first.";
        if (inputs.database().status() != ReviewStatus.APPROVED) return "Approve the database design first.";
        if (existing.isPresent() && existing.get().getStatus() != ReviewStatus.DRAFT) {
            return "The API design is " + existing.get().getStatus().label() + ". Move it back to draft to regenerate it.";
        }
        return null;
    }

    private ApiDesignOverview overview(UUID projectId) {
        Inputs inputs = inputs(projectId);
        Optional<DesignDocument> document = store.find(projectId, KIND);
        DesignState<ApiDesignContent> design = document
                .map(d -> DesignState.of(d, read(d.getContentJson()),
                        inputs.database() != null && !d.getInputFingerprint().equals(inputs.fingerprint())))
                .orElse(null);
        String blocker = blocker(inputs, document);
        return new ApiDesignOverview(
                design,
                inputs.requirements().approved().stream()
                        .map(r -> new RequirementRef(r.code(), r.type(), r.title(), r.priority()))
                        .toList(),
                inputs.database() == null ? null : inputs.database().content(),
                blocker == null,
                blocker,
                consistency.findings(projectId));
    }

    /** The current API design of a project, if there is one. */
    @Transactional(readOnly = true)
    public Optional<ApiDtos.ApiSnapshot> snapshot(UUID projectId) {
        return store.find(projectId, KIND).map(d -> new ApiDtos.ApiSnapshot(
                d.getStatus(), read(d.getContentJson()), Fingerprints.sha256(d.getContentJson())));
    }

    /** Entities and columns as compact text for AI prompts. */
    public static String renderDatabase(DatabaseDesignContent database) {
        StringBuilder text = new StringBuilder(database.summary()).append("\n");
        for (DatabaseDesignContent.Entity entity : database.entities()) {
            text.append("\n").append(entity.name());
            if (entity.description() != null) text.append(" — ").append(entity.description());
            text.append("\n");
            for (DatabaseDesignContent.Column column : entity.columns()) {
                text.append("  - ").append(column.name()).append(' ').append(column.type());
                if (column.primaryKey()) text.append(" PK");
                if (column.unique()) text.append(" UNIQUE");
                if (!column.nullable()) text.append(" NOT NULL");
                if (column.references() != null) {
                    text.append(" -> ").append(column.references().entity()).append('.').append(column.references().column());
                }
                if (!column.allowedValues().isEmpty()) text.append(" one of ").append(column.allowedValues());
                text.append("\n");
            }
        }
        return text.toString().strip();
    }

    private ApiDesignContent read(String json) {
        return jsonMapper.readValue(json, ApiDesignContent.class);
    }
}
