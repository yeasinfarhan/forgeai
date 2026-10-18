package com.forgeai.task;

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

/** One development task of a project, shown to people as T-001 … */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "seq_number", nullable = false, updatable = false)
    private int seqNumber;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType type;

    private Integer estimate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TaskSource source;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "details_json", nullable = false, columnDefinition = "text")
    private String detailsJson;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
        // Required by JPA
    }

    Task(UUID projectId, int seqNumber, int position, TaskSource source, TaskContent content, String detailsJson) {
        this.projectId = projectId;
        this.seqNumber = seqNumber;
        this.position = position;
        this.source = source;
        this.status = TaskStatus.TODO;
        apply(content, detailsJson);
    }

    /** Replaces the content. Marks AI-written tasks as edited so regeneration keeps them. */
    void edit(TaskContent content, String detailsJson) {
        apply(content, detailsJson);
        edited = true;
        updatedAt = now();
    }

    /** Changes only the stored details (e.g. a removed dependency); not a person's edit. */
    void replaceDetails(String detailsJson) {
        this.detailsJson = detailsJson;
        updatedAt = now();
    }

    void moveTo(TaskStatus target) {
        status = target;
        updatedAt = now();
    }

    void placeAt(int position) {
        this.position = position;
    }

    private void apply(TaskContent content, String detailsJson) {
        this.title = content.title();
        this.description = content.description();
        this.type = content.type();
        this.estimate = content.estimate();
        this.detailsJson = detailsJson;
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String code() {
        return code(seqNumber);
    }

    public static String code(int seqNumber) {
        return "T-%03d".formatted(seqNumber);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskType getType() {
        return type;
    }

    public Integer getEstimate() {
        return estimate;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskSource getSource() {
        return source;
    }

    public boolean isEdited() {
        return edited;
    }

    public String getDetailsJson() {
        return detailsJson;
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
