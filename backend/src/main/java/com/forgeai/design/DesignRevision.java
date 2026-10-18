package com.forgeai.design;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.forgeai.common.review.ReviewStatus;

/** Snapshot of a design document right after a change. Append-only. */
@Entity
@Table(name = "design_document_revisions")
public class DesignRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20, updatable = false)
    private DesignChange changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReviewStatus status;

    @Column(name = "content_json", nullable = false, columnDefinition = "text", updatable = false)
    private String contentJson;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DesignRevision() {
        // Required by JPA
    }

    static DesignRevision of(DesignDocument document, DesignChange change) {
        DesignRevision revision = new DesignRevision();
        revision.documentId = document.getId();
        revision.revision = document.getRevision();
        revision.changeType = change;
        revision.status = document.getStatus();
        revision.contentJson = document.getContentJson();
        revision.model = document.getModel();
        return revision;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public int getRevision() {
        return revision;
    }

    public DesignChange getChangeType() {
        return changeType;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
