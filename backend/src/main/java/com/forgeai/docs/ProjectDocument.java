package com.forgeai.docs;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A document the AI wrote for a project (README, developer guide), possibly edited since. */
@Entity
@Table(name = "project_documents")
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DocumentKind kind;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 100)
    private String model;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected ProjectDocument() {
        // Required by JPA
    }

    ProjectDocument(UUID projectId, DocumentKind kind) {
        this.projectId = projectId;
        this.kind = kind;
    }

    /** Replaces the content with a newly generated one; earlier edits are gone. */
    void generated(String content, String model, String inputFingerprint) {
        this.content = content;
        this.model = model;
        this.inputFingerprint = inputFingerprint;
        this.edited = false;
        this.generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    void edit(String content) {
        this.content = content;
        this.edited = true;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
