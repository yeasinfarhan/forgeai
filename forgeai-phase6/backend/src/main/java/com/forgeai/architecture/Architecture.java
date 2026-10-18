package com.forgeai.architecture;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.forgeai.common.review.ReviewStatus;

/**
 * The architecture proposal of one project (shares its primary key with the project). Every change
 * increments {@code revision} and is snapshotted in {@link ArchitectureRevision}.
 */
@Entity
@Table(name = "architectures")
public class Architecture {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "requirements_fingerprint", nullable = false, length = 64)
    private String requirementsFingerprint;

    @Column(nullable = false)
    private int revision;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Architecture() {
        // Required by JPA
    }

    static Architecture generated(UUID projectId, String contentJson, String model, String requirementsFingerprint) {
        Architecture architecture = new Architecture();
        architecture.projectId = projectId;
        architecture.revision = 0;
        architecture.regenerate(contentJson, model, requirementsFingerprint);
        return architecture;
    }

    /** Replaces the content with a new AI proposal; only allowed while in draft (checked by the service). */
    void regenerate(String contentJson, String model, String requirementsFingerprint) {
        this.contentJson = contentJson;
        this.model = model;
        this.requirementsFingerprint = requirementsFingerprint;
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

    public UUID getProjectId() {
        return projectId;
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

    public String getRequirementsFingerprint() {
        return requirementsFingerprint;
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
