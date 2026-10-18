package com.forgeai.architecture;

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

/** Snapshot of the architecture right after a change. Append-only. */
@Entity
@Table(name = "architecture_revisions")
public class ArchitectureRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20, updatable = false)
    private ArchitectureChange changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReviewStatus status;

    @Column(name = "content_json", nullable = false, columnDefinition = "text", updatable = false)
    private String contentJson;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArchitectureRevision() {
        // Required by JPA
    }

    static ArchitectureRevision of(Architecture architecture, ArchitectureChange change) {
        ArchitectureRevision revision = new ArchitectureRevision();
        revision.projectId = architecture.getProjectId();
        revision.revision = architecture.getRevision();
        revision.changeType = change;
        revision.status = architecture.getStatus();
        revision.contentJson = architecture.getContentJson();
        revision.model = architecture.getModel();
        return revision;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public int getRevision() {
        return revision;
    }

    public ArchitectureChange getChangeType() {
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
