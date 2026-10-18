package com.forgeai.requirement;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Snapshot of a requirement right after a change. Append-only. */
@Entity
@Table(name = "requirement_revisions")
public class RequirementRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requirement_id", nullable = false, updatable = false)
    private UUID requirementId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20, updatable = false)
    private RevisionChange changeType;

    @Column(length = 60, updatable = false)
    private String category;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String description;

    @Column(name = "acceptance_criteria", nullable = false, columnDefinition = "text", updatable = false)
    private String acceptanceCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RequirementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequirementRevision() {
        // Required by JPA
    }

    static RequirementRevision of(Requirement requirement, RevisionChange change) {
        RequirementRevision revision = new RequirementRevision();
        revision.requirementId = requirement.getId();
        revision.version = requirement.getVersion();
        revision.changeType = change;
        revision.category = requirement.getCategory();
        revision.title = requirement.getTitle();
        revision.description = requirement.getDescription();
        revision.acceptanceCriteria = requirement.getAcceptanceCriteria();
        revision.priority = requirement.getPriority();
        revision.status = requirement.getStatus();
        return revision;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public int getVersion() {
        return version;
    }

    public RevisionChange getChangeType() {
        return changeType;
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

    public List<String> acceptanceCriteriaList() {
        return Requirement.splitLines(acceptanceCriteria);
    }

    public Priority getPriority() {
        return priority;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
