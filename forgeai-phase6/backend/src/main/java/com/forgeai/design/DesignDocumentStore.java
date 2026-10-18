package com.forgeai.design;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.common.exception.ConflictException;
import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.design.DesignDtos.RevisionSummary;

/**
 * Storage and review workflow shared by the database and API design. Works on JSON text; the
 * services for each kind own the content type, its rules and the ownership checks.
 */
@Service
public class DesignDocumentStore {

    private final DesignDocumentRepository documents;
    private final DesignRevisionRepository revisions;

    DesignDocumentStore(DesignDocumentRepository documents, DesignRevisionRepository revisions) {
        this.documents = documents;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public Optional<DesignDocument> find(UUID projectId, DesignKind kind) {
        return documents.findByProjectIdAndKind(projectId, kind);
    }

    /**
     * Stores a newly generated proposal, unless the document changed while the AI was working.
     *
     * @param expectedVersion the version seen before generating; null if there was no document
     */
    @Transactional
    public void storeGenerated(
            UUID projectId, DesignKind kind, Integer expectedVersion, String contentJson, String model, String inputFingerprint) {
        DesignDocument current = documents.findByProjectIdAndKind(projectId, kind).orElse(null);
        Integer currentVersion = current == null ? null : current.getVersion();
        if (!Objects.equals(currentVersion, expectedVersion)
                || (current != null && current.getStatus() != ReviewStatus.DRAFT)) {
            throw new ConflictException("The " + kind.label() + " changed while generating. Reload and try again.");
        }
        if (current == null) {
            current = DesignDocument.generated(projectId, kind, contentJson, model, inputFingerprint);
        } else {
            current.regenerate(contentJson, model, inputFingerprint);
        }
        documents.saveAndFlush(current);
        revisions.save(DesignRevision.of(current, DesignChange.GENERATED));
    }

    /** Saves a person's edit. The caller has validated the content. */
    @Transactional
    public void edit(UUID projectId, DesignKind kind, int expectedVersion, String contentJson) {
        DesignDocument document = require(projectId, kind);
        checkVersion(document, expectedVersion);
        if (!document.getStatus().isEditable()) {
            throw new ConflictException("The " + kind.label() + " is approved. Reopen it before editing.");
        }
        document.edit(contentJson);
        documents.saveAndFlush(document);
        revisions.save(DesignRevision.of(document, DesignChange.EDITED));
    }

    /**
     * Moves the document through the review workflow.
     *
     * @param approvalBlocker why approval is not possible right now (e.g. an earlier stage is not
     *                        approved), or an empty value when it is
     */
    @Transactional
    public void changeStatus(
            UUID projectId, DesignKind kind, int expectedVersion, ReviewStatus target, Supplier<Optional<String>> approvalBlocker) {
        DesignDocument document = require(projectId, kind);
        checkVersion(document, expectedVersion);
        ReviewStatus from = document.getStatus();
        if (!from.canMoveTo(target)) {
            throw new ConflictException("The " + kind.label() + " cannot move from " + from.label() + " to " + target.label() + ".");
        }
        if (target == ReviewStatus.APPROVED) {
            approvalBlocker.get().ifPresent(reason -> {
                throw new ConflictException(reason);
            });
        }
        document.moveTo(target);
        documents.saveAndFlush(document);
        revisions.save(DesignRevision.of(document, DesignChange.STATUS_CHANGED));
    }

    @Transactional(readOnly = true)
    public List<RevisionSummary> history(UUID projectId, DesignKind kind) {
        DesignDocument document = require(projectId, kind);
        return revisions.findAllByDocumentIdOrderByRevisionDesc(document.getId()).stream()
                .map(r -> new RevisionSummary(r.getRevision(), r.getChangeType(), r.getStatus(), r.getModel(), r.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DesignRevision revision(UUID projectId, DesignKind kind, int revision) {
        DesignDocument document = require(projectId, kind);
        return revisions.findByDocumentIdAndRevision(document.getId(), revision)
                .orElseThrow(() -> new ResourceNotFoundException("Revision " + revision + " not found."));
    }

    private DesignDocument require(UUID projectId, DesignKind kind) {
        return documents.findByProjectIdAndKind(projectId, kind)
                .orElseThrow(() -> new ResourceNotFoundException("This project has no " + kind.label() + " yet. Generate one first."));
    }

    private static void checkVersion(DesignDocument document, int expectedVersion) {
        if (document.getVersion() != expectedVersion) {
            throw new ConflictException("The " + document.getKind().label()
                    + " was changed in another tab or window. Reload to see the latest version.");
        }
    }
}
