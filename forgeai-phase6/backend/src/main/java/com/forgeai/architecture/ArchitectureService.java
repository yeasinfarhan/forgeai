package com.forgeai.architecture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.architecture.ArchitectureDtos.ArchitectureOverview;
import com.forgeai.architecture.ArchitectureDtos.ArchitectureResponse;
import com.forgeai.architecture.ArchitectureDtos.ChangeArchitectureStatus;
import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.architecture.ArchitectureDtos.RevisionDetail;
import com.forgeai.architecture.ArchitectureDtos.RevisionSummary;
import com.forgeai.architecture.ArchitectureDtos.UpdateArchitecture;
import com.forgeai.common.exception.BadRequestException;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;

import tools.jackson.databind.json.JsonMapper;

/**
 * Architecture use cases (except AI generation, see {@link ArchitectureGenerationService}). Every
 * method is scoped to a project the caller owns; every change writes an {@link ArchitectureRevision}.
 */
@Service
public class ArchitectureService {

    private final ArchitectureRepository architectures;
    private final ArchitectureRevisionRepository revisions;
    private final ProjectService projects;
    private final RequirementService requirements;
    private final JsonMapper jsonMapper;

    ArchitectureService(
            ArchitectureRepository architectures,
            ArchitectureRevisionRepository revisions,
            ProjectService projects,
            RequirementService requirements,
            JsonMapper jsonMapper) {
        this.architectures = architectures;
        this.revisions = revisions;
        this.projects = projects;
        this.requirements = requirements;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ArchitectureOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return overview(projectId);
    }

    /** Saves a person's edit of the content. Not allowed once approved. */
    @Transactional
    public ArchitectureOverview update(UUID ownerId, UUID projectId, UpdateArchitecture request) {
        Architecture architecture = findOwned(ownerId, projectId);
        checkVersion(architecture, request.version());
        if (!architecture.getStatus().isEditable()) {
            throw new ConflictException("The architecture is approved. Reopen it before editing.");
        }
        ArchitectureContent content = ArchitectureRules.tidy(request.content());
        Set<String> codes = requirements.snapshot(projectId).all().stream()
                .map(r -> r.code().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> problems = ArchitectureRules.problems(content, codes);
        if (!problems.isEmpty()) {
            throw new BadRequestException(problems);
        }
        architecture.edit(jsonMapper.writeValueAsString(content));
        architectures.saveAndFlush(architecture);
        revisions.save(ArchitectureRevision.of(architecture, ArchitectureChange.EDITED));
        return overview(projectId);
    }

    @Transactional
    public ArchitectureOverview changeStatus(UUID ownerId, UUID projectId, ChangeArchitectureStatus request) {
        Architecture architecture = findOwned(ownerId, projectId);
        checkVersion(architecture, request.version());
        ReviewStatus from = architecture.getStatus();
        if (!from.canMoveTo(request.status())) {
            throw new ConflictException("The architecture cannot move from " + from.label() + " to "
                    + request.status().label() + ".");
        }
        if (request.status() == ReviewStatus.APPROVED && !requirements.snapshot(projectId).allApproved()) {
            throw new ConflictException("Approve all requirements before approving the architecture.");
        }
        architecture.moveTo(request.status());
        architectures.saveAndFlush(architecture);
        revisions.save(ArchitectureRevision.of(architecture, ArchitectureChange.STATUS_CHANGED));
        return overview(projectId);
    }

    @Transactional(readOnly = true)
    public List<RevisionSummary> history(UUID ownerId, UUID projectId) {
        findOwned(ownerId, projectId);
        return revisions.findAllByProjectIdOrderByRevisionDesc(projectId).stream()
                .map(r -> new RevisionSummary(r.getRevision(), r.getChangeType(), r.getStatus(), r.getModel(), r.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RevisionDetail revision(UUID ownerId, UUID projectId, int revision) {
        findOwned(ownerId, projectId);
        ArchitectureRevision r = revisions.findByProjectIdAndRevision(projectId, revision)
                .orElseThrow(() -> new ResourceNotFoundException("Revision " + revision + " not found."));
        return new RevisionDetail(r.getRevision(), r.getChangeType(), r.getStatus(), r.getModel(), r.getCreatedAt(),
                read(r.getContentJson()));
    }

    // --- Used by later phases --------------------------------------------------

    /** The current architecture of a project, if there is one. */
    @Transactional(readOnly = true)
    public Optional<ArchitectureSnapshot> snapshot(UUID projectId) {
        return architectures.findById(projectId).map(a -> new ArchitectureSnapshot(
                a.getStatus(), read(a.getContentJson()), Fingerprints.sha256(a.getContentJson())));
    }

    // --- Used by ArchitectureGenerationService ---------------------------------

    @Transactional(readOnly = true)
    public Optional<Architecture> find(UUID projectId) {
        return architectures.findById(projectId);
    }

    /**
     * Stores a newly generated proposal, unless the architecture changed while the AI was working
     * (another tab generated, edited or submitted it).
     *
     * @param expectedVersion the version seen before generating; null if there was no architecture
     */
    @Transactional
    public void storeGenerated(
            UUID projectId, Integer expectedVersion, ArchitectureContent content, String model, String fingerprint) {
        Architecture current = architectures.findById(projectId).orElse(null);
        Integer currentVersion = current == null ? null : current.getVersion();
        if (!Objects.equals(currentVersion, expectedVersion)
                || (current != null && current.getStatus() != ReviewStatus.DRAFT)) {
            throw new ConflictException("The architecture changed while generating. Reload and try again.");
        }
        String json = jsonMapper.writeValueAsString(content);
        if (current == null) {
            current = Architecture.generated(projectId, json, model, fingerprint);
        } else {
            current.regenerate(json, model, fingerprint);
        }
        architectures.saveAndFlush(current);
        revisions.save(ArchitectureRevision.of(current, ArchitectureChange.GENERATED));
    }

    /**
     * Identifies a set of approved requirements in their current wording: changes when one is
     * added, edited and re-approved, reopened or deleted.
     */
    static String fingerprint(List<RequirementResponse> approved) {
        String text = approved.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(r -> r.id() + ":" + r.version())
                .collect(Collectors.joining(","));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    // --- Helpers ---------------------------------------------------------------

    private ArchitectureOverview overview(UUID projectId) {
        RequirementSnapshot snapshot = requirements.snapshot(projectId);
        List<RequirementResponse> approved = snapshot.approved();
        Architecture architecture = architectures.findById(projectId).orElse(null);

        ArchitectureResponse response = null;
        if (architecture != null) {
            ArchitectureContent content = read(architecture.getContentJson());
            Set<String> covered = ArchitectureRules.coveredRequirements(content);
            response = new ArchitectureResponse(
                    architecture.getStatus(),
                    content,
                    architecture.getModel(),
                    architecture.isEdited(),
                    architecture.getRevision(),
                    architecture.getVersion(),
                    !architecture.getRequirementsFingerprint().equals(fingerprint(approved)),
                    approved.stream().map(RequirementResponse::code).filter(code -> !covered.contains(code)).toList(),
                    architecture.getCreatedAt(),
                    architecture.getUpdatedAt());
        }
        boolean canGenerate = snapshot.allApproved()
                && (architecture == null || architecture.getStatus() == ReviewStatus.DRAFT);
        return new ArchitectureOverview(
                response,
                approved.stream().map(r -> new RequirementRef(r.code(), r.type(), r.title(), r.priority())).toList(),
                snapshot.all().size(),
                approved.size(),
                canGenerate);
    }

    private ArchitectureContent read(String json) {
        return jsonMapper.readValue(json, ArchitectureContent.class);
    }

    private static void checkVersion(Architecture architecture, int expectedVersion) {
        if (architecture.getVersion() != expectedVersion) {
            throw new ConflictException("The architecture was changed in another tab or window. Reload to see the latest version.");
        }
    }

    private Architecture findOwned(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        return architectures.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("This project has no architecture yet. Generate one first."));
    }
}
