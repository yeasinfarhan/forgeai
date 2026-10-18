package com.forgeai.review;

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

import com.forgeai.task.TaskType;

/** One finding of a code review: what is wrong, where, and how to fix it. */
@Entity
@Table(name = "review_findings")
public class ReviewFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private FindingSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private FindingCategory category;

    @Column(length = 300, updatable = false)
    private String path;

    @Column(updatable = false)
    private Integer line;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String detail;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String suggestion;

    @Column(name = "requirement_code", length = 20, updatable = false)
    private String requirementCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20, updatable = false)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "task_code", length = 10)
    private String taskCode;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReviewFinding() {
        // Required by JPA
    }

    ReviewFinding(UUID reviewId, int position, ReviewAssistant.NewFinding finding) {
        this.reviewId = reviewId;
        this.position = position;
        this.severity = finding.severity();
        this.category = finding.category();
        this.path = finding.path();
        this.line = finding.line();
        this.title = finding.title();
        this.detail = finding.detail();
        this.suggestion = finding.suggestion();
        this.requirementCode = finding.requirementCode();
        this.taskType = finding.taskType();
        this.status = FindingStatus.OPEN;
    }

    void dismiss() {
        status = FindingStatus.DISMISSED;
    }

    void reopen() {
        status = FindingStatus.OPEN;
    }

    void taskCreated(UUID taskId, String taskCode) {
        this.status = FindingStatus.TASK_CREATED;
        this.taskId = taskId;
        this.taskCode = taskCode;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public int getPosition() {
        return position;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public FindingCategory getCategory() {
        return category;
    }

    public String getPath() {
        return path;
    }

    public Integer getLine() {
        return line;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
