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
import jakarta.persistence.Table;

/**
 * One proposed file change. {@code baseContent} is the accepted content when it was proposed
 * (null for a new file), so accepting can tell whether the file changed in the meantime.
 */
@Entity
@Table(name = "code_changes")
public class CodeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "changeset_id", nullable = false, updatable = false)
    private UUID changesetId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 300, updatable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ChangeAction action;

    @Column(name = "base_content", columnDefinition = "text", updatable = false)
    private String baseContent;

    @Column(name = "new_content", columnDefinition = "text", updatable = false)
    private String newContent;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeDecision decision;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected CodeChange() {
        // Required by JPA
    }

    CodeChange(UUID changesetId, int position, String path, ChangeAction action, String baseContent, String newContent,
            String explanation) {
        this.changesetId = changesetId;
        this.position = position;
        this.path = path;
        this.action = action;
        this.baseContent = baseContent;
        this.newContent = newContent;
        this.explanation = explanation;
        this.decision = ChangeDecision.PENDING;
    }

    void decide(ChangeDecision decision) {
        this.decision = decision;
        this.decidedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getChangesetId() {
        return changesetId;
    }

    public int getPosition() {
        return position;
    }

    public String getPath() {
        return path;
    }

    public ChangeAction getAction() {
        return action;
    }

    public String getBaseContent() {
        return baseContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public String getExplanation() {
        return explanation;
    }

    public ChangeDecision getDecision() {
        return decision;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
