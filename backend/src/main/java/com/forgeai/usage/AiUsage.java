package com.forgeai.usage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One AI call: who made it, for which project, what it cost in tokens and how it ended. */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    /** Outcome of a call that returned an answer. */
    static final String SUCCESS = "SUCCESS";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 60, updatable = false)
    private String purpose;

    @Column(nullable = false, length = 20, updatable = false)
    private String provider;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false, updatable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false, updatable = false)
    private int outputTokens;

    @Column(nullable = false, length = 30, updatable = false)
    private String outcome;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsage() {
        // Required by JPA
    }

    AiUsage(UUID userId, UUID projectId, String purpose, String provider, String model, int inputTokens, int outputTokens,
            String outcome, int durationMs, Instant createdAt) {
        this.userId = userId;
        this.projectId = projectId;
        this.purpose = purpose;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public String getOutcome() {
        return outcome;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
