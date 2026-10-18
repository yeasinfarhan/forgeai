package com.forgeai.code;

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

/** The AI's proposed code for one task, reviewed file by file. */
@Entity
@Table(name = "code_changesets")
public class CodeChangeset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "task_code", nullable = false, length = 10, updatable = false)
    private String taskCode;

    @Column(name = "task_title", nullable = false, length = 200, updatable = false)
    private String taskTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangesetStatus status;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String summary;

    @Column(length = 100, updatable = false)
    private String model;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CodeChangeset() {
        // Required by JPA
    }

    CodeChangeset(UUID projectId, UUID taskId, String taskCode, String taskTitle, String summary, String model) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.taskCode = taskCode;
        this.taskTitle = taskTitle;
        this.summary = summary;
        this.model = model;
        this.status = ChangesetStatus.PENDING;
    }

    void finish(ChangesetStatus status) {
        this.status = status;
        this.completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
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

    public UUID getTaskId() {
        return taskId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public ChangesetStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
