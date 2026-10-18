package com.forgeai.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** What the latest generation of a project's backlog was based on. */
@Entity
@Table(name = "task_backlogs")
public class TaskBacklog {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(length = 100)
    private String model;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected TaskBacklog() {
        // Required by JPA
    }

    TaskBacklog(UUID projectId) {
        this.projectId = projectId;
    }

    void generated(String inputFingerprint, String model) {
        this.inputFingerprint = inputFingerprint;
        this.model = model;
        this.generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public String getModel() {
        return model;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
