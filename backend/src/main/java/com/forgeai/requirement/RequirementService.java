package com.forgeai.requirement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.intake.IdeaIntakeService;
import com.forgeai.intake.IntakeStatus;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.BulkChangeStatus;
import com.forgeai.requirement.RequirementDtos.ChangeStatus;
import com.forgeai.requirement.RequirementDtos.CreateRequirement;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementDtos.RequirementsOverview;
import com.forgeai.requirement.RequirementDtos.RevisionResponse;
import com.forgeai.requirement.RequirementDtos.StatusCounts;
import com.forgeai.requirement.RequirementDtos.UpdateRequirement;

/**
 * Requirement use cases (except AI generation, see {@link RequirementGenerationService}). Every
 * method is scoped to a project the caller owns; every change writes a {@link RequirementRevision}.
 */
@Service
public class RequirementService {

    private final RequirementRepository requirements;
    private final RequirementRevisionRepository revisions;
    private final ProjectService projects;
    private final IdeaIntakeService intake;

    RequirementService(
            RequirementRepository requirements,
            RequirementRevisionRepository revisions,
            ProjectService projects,
            IdeaIntakeService intake) {
        this.requirements = requirements;
        this.revisions = revisions;
        this.projects = projects;
        this.intake = intake;
    }

    @Transactional(readOnly = true)
    public RequirementsOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        boolean briefReady = intake.get(ownerId, projectId).status() == IntakeStatus.BRIEF_READY;
        return overview(projectId, briefReady);
    }

    @Transactional
    public RequirementResponse create(UUID ownerId, UUID projectId, CreateRequirement request) {
        projects.get(ownerId, projectId);
        int next = requirements.maxSeqNumber(projectId, request.type()) + 1;
        Requirement requirement = requirements.saveAndFlush(
                new Requirement(projectId, request.type(), next, RequirementSource.USER, request.content()));
        revisions.save(RequirementRevision.of(requirement, RevisionChange.CREATED));
        return RequirementResponse.from(requirement);
    }

    @Transactional
    public RequirementResponse update(UUID ownerId, UUID projectId, UUID requirementId, UpdateRequirement request) {
        Requirement requirement = findOwned(ownerId, projectId, requirementId);
        checkVersion(requirement, request.version());
        if (!requirement.getStatus().isEditable()) {
            throw new ConflictException(requirement.code() + " is approved. Reopen it before editing.");
        }
        requirement.edit(request.content());
        requirements.saveAndFlush(requirement);
        revisions.save(RequirementRevision.of(requirement, RevisionChange.EDITED));
        return RequirementResponse.from(requirement);
    }

    @Transactional
    public RequirementResponse changeStatus(UUID ownerId, UUID projectId, UUID requirementId, ChangeStatus request) {
        Requirement requirement = findOwned(ownerId, projectId, requirementId);
        checkVersion(requirement, request.version());
        moveTo(requirement, request.status());
        return RequirementResponse.from(requirement);
    }

    /** Moves every requirement in one status to another, e.g. "approve everything in review". */
    @Transactional
    public RequirementsOverview changeStatusInBulk(UUID ownerId, UUID projectId, BulkChangeStatus request) {
        projects.get(ownerId, projectId);
        if (!request.from().canMoveTo(request.to())) {
            throw new ConflictException(transitionError(request.from(), request.to()));
        }
        for (Requirement requirement : requirements.findAllByProjectIdAndStatus(projectId, request.from())) {
            moveTo(requirement, request.to());
        }
        return overview(ownerId, projectId);
    }

    @Transactional
    public void delete(UUID ownerId, UUID projectId, UUID requirementId) {
        Requirement requirement = findOwned(ownerId, projectId, requirementId);
        if (requirement.getStatus() == ReviewStatus.APPROVED) {
            throw new ConflictException(requirement.code() + " is approved. Reopen it before deleting.");
        }
        requirements.delete(requirement);
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> history(UUID ownerId, UUID projectId, UUID requirementId) {
        Requirement requirement = findOwned(ownerId, projectId, requirementId);
        return revisions.findAllByRequirementIdOrderByVersionDesc(requirement.getId()).stream()
                .map(RevisionResponse::from)
                .toList();
    }

    // --- Used by later phases --------------------------------------------------

    /** All requirements of a project, for phases that build on approved requirements. */
    @Transactional(readOnly = true)
    public RequirementSnapshot snapshot(UUID projectId) {
        return new RequirementSnapshot(requirements.findAllByProjectIdOrderByTypeAscSeqNumberAsc(projectId).stream()
                .map(RequirementResponse::from)
                .toList());
    }

    // --- Used by RequirementGenerationService ----------------------------------

    /** Requirements that regeneration keeps: everything except untouched AI drafts. */
    @Transactional(readOnly = true)
    public List<Requirement> keptOnRegeneration(UUID projectId) {
        return requirements.findAllByProjectIdOrderByTypeAscSeqNumberAsc(projectId).stream()
                .filter(r -> !(r.getStatus() == ReviewStatus.DRAFT
                        && r.getSource() == RequirementSource.AI
                        && !r.isEdited()))
                .toList();
    }

    /** Replaces untouched AI drafts with newly generated ones, numbering after the kept requirements. */
    @Transactional
    public void replaceDrafts(UUID projectId, List<GeneratedRequirement> generated) {
        requirements.deleteReplaceableDrafts(projectId);

        Map<RequirementType, Integer> nextNumber = new EnumMap<>(RequirementType.class);
        for (RequirementType type : RequirementType.values()) {
            nextNumber.put(type, requirements.maxSeqNumber(projectId, type) + 1);
        }
        for (GeneratedRequirement item : generated) {
            int number = nextNumber.merge(item.type(), 1, Integer::sum) - 1;
            Requirement requirement = requirements.saveAndFlush(
                    new Requirement(projectId, item.type(), number, RequirementSource.AI, item.content()));
            revisions.save(RequirementRevision.of(requirement, RevisionChange.CREATED));
        }
    }

    RequirementsOverview overview(UUID projectId, boolean briefReady) {
        List<Requirement> all = requirements.findAllByProjectIdOrderByTypeAscSeqNumberAsc(projectId);
        int draft = 0;
        int inReview = 0;
        int approved = 0;
        for (Requirement r : all) {
            switch (r.getStatus()) {
                case DRAFT -> draft++;
                case IN_REVIEW -> inReview++;
                case APPROVED -> approved++;
            }
        }
        return new RequirementsOverview(
                all.stream().map(RequirementResponse::from).toList(),
                new StatusCounts(draft, inReview, approved, all.size()),
                briefReady,
                !all.isEmpty() && approved == all.size());
    }

    // --- Helpers ---------------------------------------------------------------

    private void moveTo(Requirement requirement, ReviewStatus target) {
        if (!requirement.getStatus().canMoveTo(target)) {
            throw new ConflictException(requirement.code() + ": " + transitionError(requirement.getStatus(), target));
        }
        requirement.moveTo(target);
        requirements.saveAndFlush(requirement);
        revisions.save(RequirementRevision.of(requirement, RevisionChange.STATUS_CHANGED));
    }

    private static String transitionError(ReviewStatus from, ReviewStatus to) {
        return "cannot move from " + from.label() + " to " + to.label() + ".";
    }

    private static void checkVersion(Requirement requirement, int expectedVersion) {
        if (requirement.getVersion() != expectedVersion) {
            throw new ConflictException(requirement.code()
                    + " was changed in another tab or window. Reload to see the latest version.");
        }
    }

    private Requirement findOwned(UUID ownerId, UUID projectId, UUID requirementId) {
        projects.get(ownerId, projectId);
        return requirements.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found."));
    }
}
