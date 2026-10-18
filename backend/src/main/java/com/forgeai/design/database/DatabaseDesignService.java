package com.forgeai.design.database;

import java.util.List;
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
import com.forgeai.design.consistency.ConsistencyService;
import com.forgeai.design.database.DatabaseDtos.DatabaseDesignOverview;
import com.forgeai.design.database.DatabaseDtos.DatabaseSnapshot;
import com.forgeai.design.database.DatabaseDtos.UpdateDatabaseDesign;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;

import tools.jackson.databind.json.JsonMapper;

/**
 * Database design use cases. Built from the approved architecture and requirements; generation
 * calls the AI outside any transaction, storage and workflow go through {@link DesignDocumentStore}.
 */
@Service
public class DatabaseDesignService {

    private static final DesignKind KIND = DesignKind.DATABASE;

    private final ProjectService projects;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final DesignDocumentStore store;
    private final ConsistencyService consistency;
    private final DatabaseAssistant assistant;
    private final JsonMapper jsonMapper;

    private final GenerationGuard generationGuard;

    DatabaseDesignService(
            ProjectService projects,
            RequirementService requirements,
            ArchitectureService architectures,
            DesignDocumentStore store,
            ConsistencyService consistency,
            DatabaseAssistant assistant,
            JsonMapper jsonMapper,
            GenerationGuard generationGuard) {
        this.generationGuard = generationGuard;
        this.projects = projects;
        this.requirements = requirements;
        this.architectures = architectures;
        this.store = store;
        this.consistency = consistency;
        this.assistant = assistant;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public DatabaseDesignOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return overview(projectId);
    }

    /** Generates (or regenerates, while in draft) the data model. Calls the AI; can take up to a minute. */
    public DatabaseDesignOverview generate(UUID ownerId, UUID projectId) {
        return generationGuard.run(projectId, "database-design", "database design", () -> doGenerate(ownerId, projectId));
    }

    private DatabaseDesignOverview doGenerate(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        Inputs inputs = inputs(projectId);
        Optional<DesignDocument> existing = store.find(projectId, KIND);
        String blocker = blocker(inputs, existing);
        if (blocker != null) throw new ConflictException(blocker);

        Set<String> codes = inputs.requirements().approved().stream().map(RequirementResponse::code).collect(Collectors.toSet());
        DatabaseAssistant.Generated generated = assistant.generate(project.name(),
                inputs.architecture().content().toPromptText(), inputs.requirements().approvedAsPromptText(), codes);
        try {
            store.storeGenerated(projectId, KIND, existing.map(DesignDocument::getVersion).orElse(null),
                    jsonMapper.writeValueAsString(generated.content()), generated.model(), inputs.fingerprint());
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The database design changed while generating. Reload and try again.");
        }
        return overview(ownerId, projectId);
    }

    @Transactional
    public DatabaseDesignOverview update(UUID ownerId, UUID projectId, UpdateDatabaseDesign request) {
        projects.get(ownerId, projectId);
        DatabaseDesignContent content = DatabaseRules.tidy(request.content());
        Set<String> codes = requirements.snapshot(projectId).all().stream().map(RequirementResponse::code).collect(Collectors.toSet());
        List<String> problems = DatabaseRules.problems(content, codes);
        if (!problems.isEmpty()) throw new BadRequestException(problems);
        store.edit(projectId, KIND, request.version(), jsonMapper.writeValueAsString(content));
        return overview(projectId);
    }

    @Transactional
    public DatabaseDesignOverview changeStatus(UUID ownerId, UUID projectId, ChangeDesignStatus request) {
        projects.get(ownerId, projectId);
        store.changeStatus(projectId, KIND, request.version(), request.status(), () -> {
            Inputs inputs = inputs(projectId);
            if (!inputs.requirements().allApproved()) return Optional.of("Approve all requirements before approving the database design.");
            if (inputs.architecture() == null || inputs.architecture().status() != ReviewStatus.APPROVED) {
                return Optional.of("Approve the architecture before approving the database design.");
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
    public RevisionDetail<DatabaseDesignContent> revision(UUID ownerId, UUID projectId, int revision) {
        projects.get(ownerId, projectId);
        DesignRevision r = store.revision(projectId, KIND, revision);
        return new RevisionDetail<>(r.getRevision(), r.getChangeType(), r.getStatus(), r.getModel(), r.getCreatedAt(), read(r.getContentJson()));
    }

    /** The current data model as a PostgreSQL schema script. */
    @Transactional(readOnly = true)
    public DesignExport exportSql(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        DesignDocument document = store.find(projectId, KIND)
                .orElseThrow(() -> new ConflictException("Generate the database design first."));
        String sql = SqlExporter.export(read(document.getContentJson()), List.of(
                "PostgreSQL schema for \"" + project.name() + "\"",
                "Generated by ForgeAI from database design revision " + document.getRevision()
                        + " (" + document.getStatus().label() + ")",
                "Exported " + document.getUpdatedAt()));
        return new DesignExport(DesignExport.slug(project.name()) + "-schema.sql", "application/sql", sql);
    }

    // --- Used by the API design --------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<DatabaseSnapshot> snapshot(UUID projectId) {
        return store.find(projectId, KIND).map(d -> new DatabaseSnapshot(
                d.getStatus(), read(d.getContentJson()), Fingerprints.sha256(d.getContentJson())));
    }

    // --- Helpers -----------------------------------------------------------------

    /** What the data model is built from, and their combined fingerprint. */
    private record Inputs(ArchitectureSnapshot architecture, RequirementSnapshot requirements) {

        String fingerprint() {
            return Fingerprints.sha256(architecture == null ? "" : architecture.fingerprint(), requirements.approvedFingerprint());
        }
    }

    private Inputs inputs(UUID projectId) {
        return new Inputs(architectures.snapshot(projectId).orElse(null), requirements.snapshot(projectId));
    }

    private static String blocker(Inputs inputs, Optional<DesignDocument> existing) {
        RequirementSnapshot requirements = inputs.requirements();
        if (requirements.all().isEmpty()) return "Add and approve requirements first.";
        if (!requirements.allApproved()) {
            return "Approve all requirements first: " + requirements.approved().size() + " of " + requirements.all().size()
                    + " are approved.";
        }
        if (inputs.architecture() == null) return "Generate and approve the architecture first.";
        if (inputs.architecture().status() != ReviewStatus.APPROVED) return "Approve the architecture first.";
        if (existing.isPresent() && existing.get().getStatus() != ReviewStatus.DRAFT) {
            return "The database design is " + existing.get().getStatus().label() + ". Move it back to draft to regenerate it.";
        }
        return null;
    }

    private DatabaseDesignOverview overview(UUID projectId) {
        Inputs inputs = inputs(projectId);
        Optional<DesignDocument> document = store.find(projectId, KIND);
        DesignState<DatabaseDesignContent> design = document
                .map(d -> DesignState.of(d, read(d.getContentJson()),
                        inputs.architecture() != null && !d.getInputFingerprint().equals(inputs.fingerprint())))
                .orElse(null);
        String blocker = blocker(inputs, document);
        return new DatabaseDesignOverview(
                design,
                inputs.requirements().approved().stream()
                        .map(r -> new RequirementRef(r.code(), r.type(), r.title(), r.priority()))
                        .toList(),
                blocker == null,
                blocker,
                consistency.findings(projectId));
    }

    private DatabaseDesignContent read(String json) {
        return jsonMapper.readValue(json, DatabaseDesignContent.class);
    }
}
