package com.forgeai.requirement;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
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

/**
 * One requirement of a project, identified to people by a stable code such as {@code FR-003}.
 * Every change is versioned (optimistic locking) and snapshotted in {@link RequirementRevision}.
 */
@Entity
@Table(name = "requirements")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RequirementType type;

    @Column(name = "seq_number", nullable = false, updatable = false)
    private int seqNumber;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "acceptance_criteria", nullable = false, columnDefinition = "text")
    private String acceptanceCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private RequirementSource source;

    @Column(nullable = false)
    private boolean edited;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Requirement() {
        // Required by JPA
    }

    Requirement(UUID projectId, RequirementType type, int seqNumber, RequirementSource source, RequirementContent content) {
        this.projectId = projectId;
        this.type = type;
        this.seqNumber = seqNumber;
        this.source = source;
        this.status = RequirementStatus.DRAFT;
        apply(content);
    }

    /** Replaces the content. Marks AI-written requirements as edited so regeneration keeps them. */
    void edit(RequirementContent content) {
        apply(content);
        edited = true;
        updatedAt = now();
    }

    void moveTo(RequirementStatus target) {
        status = target;
        updatedAt = now();
    }

    private void apply(RequirementContent content) {
        this.category = content.category();
        this.title = content.title();
        this.description = content.description();
        this.acceptanceCriteria = String.join("\n", content.acceptanceCriteria());
        this.priority = content.priority();
    }

    @PrePersist
    void onCreate() {
        Instant now = now();
        createdAt = now;
        updatedAt = now;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String code() {
        return type.code(seqNumber);
    }

    public List<String> acceptanceCriteriaList() {
        return splitLines(acceptanceCriteria);
    }

    static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\n")).filter(line -> !line.isBlank()).toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public RequirementType getType() {
        return type;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public Priority getPriority() {
        return priority;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public RequirementSource getSource() {
        return source;
    }

    public boolean isEdited() {
        return edited;
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
