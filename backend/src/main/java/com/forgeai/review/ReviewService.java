package com.forgeai.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.code.CodeService;
import com.forgeai.code.CodeService.SourceFile;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.project.ProjectService;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.review.ReviewDtos.FindingCounts;
import com.forgeai.review.ReviewDtos.FindingResponse;
import com.forgeai.review.ReviewDtos.ReviewOverview;
import com.forgeai.review.ReviewDtos.ReviewResponse;
import com.forgeai.review.ReviewDtos.ReviewSummary;
import com.forgeai.task.TaskDtos.SaveTask;
import com.forgeai.task.TaskService;
import com.forgeai.task.TaskService.AddedTask;

/**
 * Code reviews and what a person does with their findings: turn one into a backlog task, dismiss
 * it, or reopen a dismissed one. Reviews themselves are written by {@link ReviewGenerationService}.
 */
@Service
public class ReviewService {

    private final CodeReviewRepository reviews;
    private final ReviewFindingRepository findings;
    private final ProjectService projects;
    private final CodeService code;
    private final TaskService tasks;
    private final RequirementService requirements;

    ReviewService(
            CodeReviewRepository reviews,
            ReviewFindingRepository findings,
            ProjectService projects,
            CodeService code,
            TaskService tasks,
            RequirementService requirements) {
        this.reviews = reviews;
        this.findings = findings;
        this.projects = projects;
        this.code = code;
        this.tasks = tasks;
        this.requirements = requirements;
    }

    @Transactional(readOnly = true)
    public ReviewOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        List<SourceFile> files = code.sourceFiles(projectId);
        String blocker = blocker(projectId, files);
        String fingerprint = fingerprint(files);
        List<CodeReview> all = reviews.findAllByProjectIdOrderByCreatedAtDesc(projectId);
        Map<UUID, List<ReviewFinding>> byReview = findings.findAllByReviewIdIn(all.stream().map(CodeReview::getId).toList())
                .stream().collect(Collectors.groupingBy(ReviewFinding::getReviewId));
        return new ReviewOverview(
                blocker == null,
                blocker,
                files.size(),
                all.stream()
                        .map(r -> new ReviewSummary(r.getId(), r.getCreatedAt(), r.getModel(), r.getFilesReviewed(), r.getFilesTotal(),
                                counts(byReview.getOrDefault(r.getId(), List.of()))))
                        .toList(),
                all.isEmpty() ? null : response(all.getFirst(), fingerprint));
    }

    @Transactional(readOnly = true)
    public ReviewResponse review(UUID ownerId, UUID projectId, UUID reviewId) {
        projects.get(ownerId, projectId);
        return response(find(projectId, reviewId), fingerprint(code.sourceFiles(projectId)));
    }

    /** Adds a backlog task that fixes the finding, linked to its requirement if that is still approved. */
    @Transactional
    public ReviewResponse createTask(UUID ownerId, UUID projectId, UUID reviewId, UUID findingId) {
        projects.get(ownerId, projectId);
        CodeReview review = find(projectId, reviewId);
        ReviewFinding finding = findFinding(review, findingId);
        if (finding.getStatus() != FindingStatus.OPEN) {
            throw new ConflictException(finding.getStatus() == FindingStatus.TASK_CREATED
                    ? "A task was already created for this finding (" + finding.getTaskCode() + ")."
                    : "This finding is dismissed. Reopen it first.");
        }
        Set<String> approved = requirements.snapshot(projectId).approved().stream()
                .map(RequirementResponse::code).collect(Collectors.toSet());
        AddedTask task = tasks.add(projectId, taskFor(finding, approved));
        finding.taskCreated(task.id(), task.code());
        findings.saveAndFlush(finding);
        return response(review, fingerprint(code.sourceFiles(projectId)));
    }

    @Transactional
    public ReviewResponse dismiss(UUID ownerId, UUID projectId, UUID reviewId, UUID findingId) {
        projects.get(ownerId, projectId);
        CodeReview review = find(projectId, reviewId);
        ReviewFinding finding = findFinding(review, findingId);
        if (finding.getStatus() != FindingStatus.OPEN) {
            throw new ConflictException("Only open findings can be dismissed.");
        }
        finding.dismiss();
        findings.saveAndFlush(finding);
        return response(review, fingerprint(code.sourceFiles(projectId)));
    }

    @Transactional
    public ReviewResponse reopen(UUID ownerId, UUID projectId, UUID reviewId, UUID findingId) {
        projects.get(ownerId, projectId);
        CodeReview review = find(projectId, reviewId);
        ReviewFinding finding = findFinding(review, findingId);
        if (finding.getStatus() != FindingStatus.DISMISSED) {
            throw new ConflictException("Only dismissed findings can be reopened.");
        }
        finding.reopen();
        findings.saveAndFlush(finding);
        return response(review, fingerprint(code.sourceFiles(projectId)));
    }

    // --- Used by ReviewGenerationService --------------------------------------------

    /** Why the code cannot be reviewed right now; null when it can. */
    String blocker(UUID projectId, List<SourceFile> files) {
        String design = tasks.designBlocker(projectId);
        if (design != null) return design;
        if (files.isEmpty()) return "There is no accepted code to review yet. Generate and accept code on the Code page first.";
        return null;
    }

    @Transactional
    public ReviewResponse store(UUID projectId, ReviewAssistant.Result result, String codeFingerprint) {
        CodeReview review = reviews.saveAndFlush(new CodeReview(projectId, result.summary(), result.model(),
                result.filesReviewed(), result.filesTotal(), codeFingerprint));
        for (int i = 0; i < result.findings().size(); i++) {
            findings.save(new ReviewFinding(review.getId(), i, result.findings().get(i)));
        }
        findings.flush();
        return response(review, fingerprint(code.sourceFiles(projectId)));
    }

    /** Identifies the accepted code exactly: changes when any file is added, changed or deleted. */
    static String fingerprint(List<SourceFile> files) {
        List<String> parts = new ArrayList<>();
        for (SourceFile file : files) {
            parts.add(file.path());
            parts.add(file.content());
        }
        return Fingerprints.sha256(parts.toArray(String[]::new));
    }

    // --- Helpers -------------------------------------------------------------------

    static SaveTask taskFor(ReviewFinding finding, Set<String> approvedRequirements) {
        StringBuilder description = new StringBuilder(finding.getDetail())
                .append("\n\nSuggested fix: ").append(finding.getSuggestion());
        if (finding.getPath() != null) {
            description.append("\n\nFound by the code review in ").append(finding.getPath());
            if (finding.getLine() != null) description.append(", line ").append(finding.getLine());
            description.append('.');
        } else {
            description.append("\n\nFound by the code review.");
        }
        String text = description.length() <= 4_000 ? description.toString() : description.substring(0, 4_000).strip();
        String criterion = "The review finding is resolved: " + finding.getTitle();
        List<String> requirementCodes = finding.getRequirementCode() != null && approvedRequirements.contains(finding.getRequirementCode())
                ? List.of(finding.getRequirementCode()) : List.of();
        return new SaveTask(
                finding.getTitle(),
                text,
                finding.getTaskType(),
                null,
                List.of(criterion.length() <= 500 ? criterion : criterion.substring(0, 500).strip()),
                List.of(),
                requirementCodes,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private ReviewResponse response(CodeReview review, String currentFingerprint) {
        List<ReviewFinding> list = findings.findAllByReviewIdOrderByPositionAsc(review.getId());
        return new ReviewResponse(review.getId(), review.getSummary(), review.getModel(), review.getCreatedAt(),
                review.getFilesReviewed(), review.getFilesTotal(), !review.getCodeFingerprint().equals(currentFingerprint),
                counts(list),
                list.stream()
                        .map(f -> new FindingResponse(f.getId(), f.getSeverity(), f.getCategory(), f.getPath(), f.getLine(), f.getTitle(),
                                f.getDetail(), f.getSuggestion(), f.getRequirementCode(), f.getTaskType(), f.getStatus(), f.getTaskId(),
                                f.getTaskCode(), f.getUpdatedAt()))
                        .toList());
    }

    private static FindingCounts counts(List<ReviewFinding> list) {
        int high = 0;
        int medium = 0;
        int low = 0;
        int open = 0;
        int dismissed = 0;
        int taskCreated = 0;
        for (ReviewFinding f : list) {
            switch (f.getSeverity()) {
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
            }
            switch (f.getStatus()) {
                case OPEN -> open++;
                case DISMISSED -> dismissed++;
                case TASK_CREATED -> taskCreated++;
            }
        }
        return new FindingCounts(list.size(), high, medium, low, open, dismissed, taskCreated);
    }

    private CodeReview find(UUID projectId, UUID reviewId) {
        return reviews.findByIdAndProjectId(reviewId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found."));
    }

    private ReviewFinding findFinding(CodeReview review, UUID findingId) {
        return findings.findByIdAndReviewId(findingId, review.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found."));
    }
}
