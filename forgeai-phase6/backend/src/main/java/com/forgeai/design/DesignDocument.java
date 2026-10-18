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
import jakarta.persistence.Version;

import com.forgeai.common.review.ReviewStatus;

/**
 * One design document (database or API) of a project. Every change increments {@code revision}
 * and is snapshotted in {@link DesignRevision}.
 */
@Entity
@Table(name = "design_documents")
public class DesignDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DesignKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(nullable = false)
    private int revision;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DesignDocument() {
        // Required by JPA
    }

    static DesignDocument generated(UUID projectId, DesignKind kind, String contentJson, String model, String inputFingerprint) {
        DesignDocument document = new DesignDocument();
        document.projectId = projectId;
        document.kind = kind;
        document.regenerate(contentJson, model, inputFingerprint);
        return document;
    }

    /** Replaces the content with a new AI proposal; only allowed in draft (checked by the store). */
    void regenerate(String contentJson, String model, String inputFingerprint) {
        this.contentJson = contentJson;
        this.model = model;
        this.inputFingerprint = inputFingerprint;
        this.edited = false;
        this.status = ReviewStatus.DRAFT;
        nextRevision();
    }

    void edit(String contentJson) {
        this.contentJson = contentJson;
        this.edited = true;
        nextRevision();
    }

    void moveTo(ReviewStatus target) {
        this.status = target;
        nextRevision();
    }

    private void nextRevision() {
        revision++;
        updatedAt = now();
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public DesignKind getKind() {
        return kind;
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

    public boolean isEdited() {
        return edited;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public int getRevision() {
        return revision;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
