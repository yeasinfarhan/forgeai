package com.forgeai.code;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.code.CodeDtos.ChangeResponse;
import com.forgeai.code.CodeDtos.ChangesetResponse;
import com.forgeai.code.CodeDtos.ChangesetSummary;
import com.forgeai.code.CodeDtos.CodeOverview;
import com.forgeai.code.CodeDtos.FileContent;
import com.forgeai.code.CodeDtos.FileSummary;
import com.forgeai.code.CodeDtos.TaskItem;
import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.design.DesignExport;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.task.TaskService;
import com.forgeai.task.TaskService.CodeTask;

/**
 * The code workspace and the review of proposed change sets. Accepting a file writes it to the
 * workspace, but only if the file is still exactly as it was when the change was proposed;
 * otherwise the person would silently overwrite newer code.
 */
@Service
public class CodeService {

    private final CodeFileRepository files;
    private final CodeChangesetRepository changesets;
    private final CodeChangeRepository changes;
    private final ProjectService projects;
    private final TaskService tasks;

    CodeService(
            CodeFileRepository files,
            CodeChangesetRepository changesets,
            CodeChangeRepository changes,
            ProjectService projects,
            TaskService tasks) {
        this.files = files;
        this.changesets = changesets;
        this.changes = changes;
        this.projects = projects;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public CodeOverview overview(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId);
        List<CodeChangeset> all = changesets.findAllByProjectIdOrderByCreatedAtDesc(projectId);
        Map<UUID, List<CodeChange>> changesBySet = changes.findAllByChangesetIdIn(all.stream().map(CodeChangeset::getId).toList())
                .stream().collect(Collectors.groupingBy(CodeChange::getChangesetId));
        Map<UUID, UUID> pendingByTask = new HashMap<>();
        all.stream()
                .filter(c -> c.getStatus() == ChangesetStatus.PENDING && c.getTaskId() != null)
                .forEach(c -> pendingByTask.putIfAbsent(c.getTaskId(), c.getId()));

        List<CodeTask> codeTasks = tasks.codeTasks(projectId);
        String blocker = blocker(projectId, codeTasks);
        return new CodeOverview(
                files.findAllByProjectIdOrderByPathAsc(projectId).stream()
                        .map(f -> new FileSummary(f.getPath(), CodeAssistant.lines(f.getContent()),
                                f.getContent().getBytes(StandardCharsets.UTF_8).length, f.getLastTaskCode(), f.getUpdatedAt()))
                        .toList(),
                codeTasks.stream()
                        .map(t -> new TaskItem(t.id(), t.code(), t.title(), t.status(), t.blockedBy(), pendingByTask.get(t.id())))
                        .toList(),
                all.stream().map(c -> summary(c, changesBySet.getOrDefault(c.getId(), List.of()))).toList(),
                blocker == null,
                blocker);
    }

    @Transactional(readOnly = true)
    public FileContent file(UUID ownerId, UUID projectId, String path) {
        projects.get(ownerId, projectId);
        CodeFile file = files.findByProjectIdAndPath(projectId, path == null ? "" : path.strip())
                .orElseThrow(() -> new ResourceNotFoundException("File not found."));
        return new FileContent(file.getPath(), file.getContent(), file.getLastTaskCode(), file.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ChangesetResponse changeset(UUID ownerId, UUID projectId, UUID changesetId) {
        projects.get(ownerId, projectId);
        return response(findChangeset(projectId, changesetId));
    }

    /** Accepts one file: writes it to the workspace if the file is unchanged since the proposal. */
    @Transactional
    public ChangesetResponse accept(UUID ownerId, UUID projectId, UUID changesetId, UUID changeId) {
        projects.get(ownerId, projectId);
        CodeChangeset changeset = requirePending(findChangeset(projectId, changesetId));
        apply(changeset, requirePendingChange(changeset, changeId));
        completeIfDecided(changeset);
        return response(changeset);
    }

    @Transactional
    public ChangesetResponse reject(UUID ownerId, UUID projectId, UUID changesetId, UUID changeId) {
        projects.get(ownerId, projectId);
        CodeChangeset changeset = requirePending(findChangeset(projectId, changesetId));
        CodeChange change = requirePendingChange(changeset, changeId);
        change.decide(ChangeDecision.REJECTED);
        changes.saveAndFlush(change);
        completeIfDecided(changeset);
        return response(changeset);
    }

    /** Accepts every undecided file at once; nothing is written if any of them is stale. */
    @Transactional
    public ChangesetResponse acceptAll(UUID ownerId, UUID projectId, UUID changesetId) {
        projects.get(ownerId, projectId);
        CodeChangeset changeset = requirePending(findChangeset(projectId, changesetId));
        for (CodeChange change : changes.findAllByChangesetIdOrderByPositionAsc(changeset.getId())) {
            if (change.getDecision() == ChangeDecision.PENDING) apply(changeset, change);
        }
        completeIfDecided(changeset);
        return response(changeset);
    }

    /** Drops the files still undecided; accepted files stay in the workspace. */
    @Transactional
    public ChangesetResponse discard(UUID ownerId, UUID projectId, UUID changesetId) {
        projects.get(ownerId, projectId);
        CodeChangeset changeset = requirePending(findChangeset(projectId, changesetId));
        changeset.finish(ChangesetStatus.DISCARDED);
        changesets.saveAndFlush(changeset);
        return response(changeset);
    }

    /** The accepted code as a ZIP archive, inside a folder named after the project. */
    @Transactional(readOnly = true)
    public ZipExport export(UUID ownerId, UUID projectId) {
        ProjectResponse project = projects.get(ownerId, projectId);
        List<CodeFile> all = files.findAllByProjectIdOrderByPathAsc(projectId);
        if (all.isEmpty()) throw new ConflictException("There is no accepted code to export yet.");
        String folder = DesignExport.slug(project.name());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (CodeFile file : all) {
                zip.putNextEntry(new ZipEntry(folder + "/" + file.getPath()));
                zip.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the archive", ex);
        }
        return new ZipExport(folder + ".zip", bytes.toByteArray());
    }

    public record ZipExport(String fileName, byte[] bytes) {
    }

    // --- Used by CodeGenerationService --------------------------------------------

    /** Why code cannot be generated right now; null when it can. */
    String blocker(UUID projectId, List<CodeTask> codeTasks) {
        String design = tasks.designBlocker(projectId);
        if (design != null) return design;
        if (codeTasks.isEmpty()) return "Generate the development tasks first.";
        return null;
    }

    @Transactional(readOnly = true)
    public List<CodeAssistant.WorkspaceFile> workspace(UUID projectId) {
        return files.findAllByProjectIdOrderByPathAsc(projectId).stream()
                .map(f -> new CodeAssistant.WorkspaceFile(f.getPath(), f.getContent(), f.getLastTaskCode(), f.getUpdatedAt()))
                .toList();
    }

    /**
     * Stores a new proposal for a task, replacing any proposal for it still waiting for review,
     * and moves the task to in progress if it was still to do.
     */
    @Transactional
    public CodeChangeset store(UUID projectId, CodeTask task, CodeAssistant.Generated generated) {
        for (CodeChangeset previous : changesets.findAllByProjectIdAndTaskIdAndStatus(projectId, task.id(), ChangesetStatus.PENDING)) {
            previous.finish(ChangesetStatus.DISCARDED);
            changesets.save(previous);
        }
        CodeChangeset changeset = changesets.saveAndFlush(
                new CodeChangeset(projectId, task.id(), task.code(), task.title(), generated.summary(), generated.model()));
        List<CodeAssistant.Proposed> proposed = generated.changes();
        for (int i = 0; i < proposed.size(); i++) {
            CodeAssistant.Proposed p = proposed.get(i);
            changes.save(new CodeChange(changeset.getId(), i, p.path(), p.action(), p.baseContent(), p.newContent(), p.explanation()));
        }
        changes.flush();
        tasks.markStarted(projectId, task.id());
        return changeset;
    }

    @Transactional(readOnly = true)
    public ChangesetResponse response(UUID projectId, UUID changesetId) {
        return response(findChangeset(projectId, changesetId));
    }

    // --- Helpers -------------------------------------------------------------------

    private void apply(CodeChangeset changeset, CodeChange change) {
        CodeFile current = files.findByProjectIdAndPath(changeset.getProjectId(), change.getPath()).orElse(null);
        if (stale(change, current)) {
            throw new ConflictException(change.getPath() + " changed after this proposal was made. "
                    + "Reject it and generate the task's code again.");
        }
        switch (change.getAction()) {
            case CREATE -> files.save(new CodeFile(changeset.getProjectId(), change.getPath(), change.getNewContent(), changeset.getTaskCode()));
            case UPDATE -> {
                current.write(change.getNewContent(), changeset.getTaskCode());
                files.save(current);
            }
            case DELETE -> files.delete(current);
        }
        files.flush();
        change.decide(ChangeDecision.ACCEPTED);
        changes.saveAndFlush(change);
    }

    /** The file is no longer what the proposal was based on. */
    private static boolean stale(CodeChange change, CodeFile current) {
        return switch (change.getAction()) {
            case CREATE -> current != null;
            case UPDATE, DELETE -> current == null || !current.getContent().equals(change.getBaseContent());
        };
    }

    private void completeIfDecided(CodeChangeset changeset) {
        boolean undecided = changes.findAllByChangesetIdOrderByPositionAsc(changeset.getId()).stream()
                .anyMatch(c -> c.getDecision() == ChangeDecision.PENDING);
        if (!undecided) {
            changeset.finish(ChangesetStatus.COMPLETED);
            changesets.saveAndFlush(changeset);
        }
    }

    private CodeChangeset findChangeset(UUID projectId, UUID changesetId) {
        return changesets.findByIdAndProjectId(changesetId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Change set not found."));
    }

    private static CodeChangeset requirePending(CodeChangeset changeset) {
        if (changeset.getStatus() != ChangesetStatus.PENDING) {
            throw new ConflictException("This change set is " + changeset.getStatus().name().toLowerCase() + "; nothing is left to review.");
        }
        return changeset;
    }

    private CodeChange requirePendingChange(CodeChangeset changeset, UUID changeId) {
        CodeChange change = changes.findByIdAndChangesetId(changeId, changeset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File change not found."));
        if (change.getDecision() != ChangeDecision.PENDING) {
            throw new ConflictException(change.getPath() + " was already " + change.getDecision().name().toLowerCase() + ".");
        }
        return change;
    }

    private ChangesetResponse response(CodeChangeset changeset) {
        Map<String, CodeFile> current = files.findAllByProjectIdOrderByPathAsc(changeset.getProjectId()).stream()
                .collect(Collectors.toMap(CodeFile::getPath, Function.identity()));
        List<ChangeResponse> list = new ArrayList<>();
        for (CodeChange c : changes.findAllByChangesetIdOrderByPositionAsc(changeset.getId())) {
            boolean stale = c.getDecision() == ChangeDecision.PENDING && changeset.getStatus() == ChangesetStatus.PENDING
                    && stale(c, current.get(c.getPath()));
            list.add(new ChangeResponse(c.getId(), c.getPath(), c.getAction(), c.getBaseContent(), c.getNewContent(),
                    c.getExplanation(), c.getDecision(), stale, c.getDecidedAt()));
        }
        return new ChangesetResponse(changeset.getId(), changeset.getTaskId(), changeset.getTaskCode(), changeset.getTaskTitle(),
                changeset.getStatus(), changeset.getSummary(), changeset.getModel(), changeset.getCreatedAt(),
                changeset.getCompletedAt(), list);
    }

    private static ChangesetSummary summary(CodeChangeset c, List<CodeChange> list) {
        int pending = 0;
        int accepted = 0;
        int rejected = 0;
        for (CodeChange change : list) {
            switch (change.getDecision()) {
                case PENDING -> pending++;
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
            }
        }
        return new ChangesetSummary(c.getId(), c.getTaskId(), c.getTaskCode(), c.getTaskTitle(), c.getStatus(), c.getSummary(),
                pending, accepted, rejected, c.getCreatedAt());
    }
}
