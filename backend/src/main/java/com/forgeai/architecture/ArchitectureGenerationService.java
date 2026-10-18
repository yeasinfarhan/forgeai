package com.forgeai.architecture;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.forgeai.architecture.ArchitectureDtos.ArchitectureOverview;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.intake.IdeaIntakeService;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementSnapshot;

/**
 * Generates the architecture proposal from the approved requirements (and the brief, if any). Not
 * transactional: the AI call can take a while, so reads and the final write happen in short
 * transactions in {@link ArchitectureService}.
 *
 * <p>Only allowed while every requirement is approved and the architecture is absent or in draft;
 * a reviewed or approved architecture must be moved back to draft first. The replaced content stays
 * in the revision history.
 */
@Service
public class ArchitectureGenerationService {

    private final ProjectService projects;
    private final IdeaIntakeService intake;
    private final RequirementService requirements;
    private final ArchitectureService architectures;
    private final ArchitectureAssistant assistant;

    ArchitectureGenerationService(
            ProjectService projects,
            IdeaIntakeService intake,
            RequirementService requirements,
            ArchitectureService architectures,
            ArchitectureAssistant assistant) {
        this.projects = projects;
        this.intake = intake;
        this.requirements = requirements;
        this.architectures = architectures;
        this.assistant = assistant;
    }

    public ArchitectureOverview generate(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);

        RequirementSnapshot snapshot = requirements.snapshot(projectId);
        if (snapshot.all().isEmpty()) {
            throw new ConflictException("Add and approve requirements first (Requirements on the project page).");
        }
        if (!snapshot.allApproved()) {
            throw new ConflictException("Approve all requirements first: " + snapshot.approved().size() + " of "
                    + snapshot.all().size() + " are approved.");
        }

        Architecture existing = architectures.find(projectId).orElse(null);
        if (existing != null && existing.getStatus() != ReviewStatus.DRAFT) {
            throw new ConflictException("The architecture is " + existing.getStatus().label()
                    + ". Move it back to draft before regenerating.");
        }
        Integer expectedVersion = existing == null ? null : existing.getVersion();

        ArchitectureAssistant.Generated generated = assistant.generate(
                project.name(), intake.get(ownerId, projectId).brief(), snapshot.approved());
        try {
            architectures.storeGenerated(projectId, expectedVersion, generated.content(), generated.model(),
                    ArchitectureService.fingerprint(snapshot.approved()));
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The architecture changed while generating. Reload and try again.");
        }
        return architectures.overview(ownerId, projectId);
    }
}
