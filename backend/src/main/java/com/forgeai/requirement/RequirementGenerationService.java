package com.forgeai.requirement;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.forgeai.common.concurrent.GenerationGuard;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.intake.IdeaIntakeService;
import com.forgeai.intake.IntakeResponse;
import com.forgeai.intake.IntakeStatus;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementsOverview;

/**
 * Generates requirements from the project brief. Not transactional: the AI call can take a while,
 * so reads and writes happen in short transactions in {@link RequirementService}.
 *
 * <p>Regeneration never touches approved, in-review, edited or hand-written requirements; it
 * replaces only AI drafts nobody has changed, and tells the AI what already exists.
 */
@Service
public class RequirementGenerationService {

    private final ProjectService projects;
    private final IdeaIntakeService intake;
    private final RequirementService requirements;
    private final RequirementAssistant assistant;

    private final GenerationGuard generationGuard;

    RequirementGenerationService(
            ProjectService projects, IdeaIntakeService intake, RequirementService requirements, RequirementAssistant assistant, GenerationGuard generationGuard) {
        this.generationGuard = generationGuard;
        this.projects = projects;
        this.intake = intake;
        this.requirements = requirements;
        this.assistant = assistant;
    }

    public RequirementsOverview generate(UUID ownerId, UUID projectId) {
        return generationGuard.run(projectId, "requirements", "requirement list", () -> doGenerate(ownerId, projectId));
    }

    private RequirementsOverview doGenerate(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        IntakeResponse intakeState = intake.get(ownerId, projectId);
        if (intakeState.status() != IntakeStatus.BRIEF_READY || intakeState.brief() == null) {
            throw new ConflictException("Write the project brief first (Idea intake on the project page).");
        }

        List<Requirement> kept = requirements.keptOnRegeneration(projectId);
        List<GeneratedRequirement> generated = assistant.generate(intakeState, kept);
        try {
            requirements.replaceDrafts(projectId, generated);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Requirements changed while generating. Reload and try again.");
        }
        return requirements.overview(projectId, true);
    }
}
