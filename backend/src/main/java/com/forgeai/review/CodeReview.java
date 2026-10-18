package com.forgeai.review;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One AI review of a project's accepted code. Immutable once stored; its findings change. */
@Entity
@Table(name = "code_reviews")
public class CodeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String summary;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "files_reviewed", nullable = false, updatable = false)
    private int filesReviewed;

    @Column(name = "files_total", nullable = false, updatable = false)
    private int filesTotal;

    @Column(name = "code_fingerprint", nullable = false, length = 64, updatable = false)
    private String codeFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CodeReview() {
        // Required by JPA
    }

    CodeReview(UUID projectId, String summary, String model, int filesReviewed, int filesTotal, String codeFingerprint) {
        this.projectId = projectId;
        this.summary = summary;
        this.model = model;
        this.filesReviewed = filesReviewed;
        this.filesTotal = filesTotal;
        this.codeFingerprint = codeFingerprint;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getSummary() {
        return summary;
    }

    public String getModel() {
        return model;
    }

    public int getFilesReviewed() {
        return filesReviewed;
    }

    public int getFilesTotal() {
        return filesTotal;
    }

    public String getCodeFingerprint() {
        return codeFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
