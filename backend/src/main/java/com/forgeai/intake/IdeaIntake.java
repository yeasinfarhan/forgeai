package com.forgeai.intake;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The idea intake of one project. Shares its primary key with the project. */
@Entity
@Table(name = "idea_intakes")
public class IdeaIntake {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String idea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntakeStatus status;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "brief_json", columnDefinition = "text")
    private String briefJson;

    @Column(name = "brief_model", length = 100)
    private String briefModel;

    /** Optimistic lock: detects two browser tabs answering the same round at once. */
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdeaIntake() {
        // Required by JPA
    }

    private IdeaIntake(UUID projectId, String idea) {
        this.projectId = projectId;
        this.idea = idea;
    }

    static IdeaIntake awaitingAnswers(UUID projectId, String idea) {
        IdeaIntake intake = new IdeaIntake(projectId, idea);
        intake.status = IntakeStatus.AWAITING_ANSWERS;
        intake.currentRound = 1;
        return intake;
    }

    static IdeaIntake withBrief(UUID projectId, String idea, String briefJson, String model) {
        IdeaIntake intake = new IdeaIntake(projectId, idea);
        intake.currentRound = 0;
        intake.completeWith(briefJson, model);
        return intake;
    }

    void startNextRound() {
        currentRound++;
        status = IntakeStatus.AWAITING_ANSWERS;
        touch();
    }

    void completeWith(String briefJson, String model) {
        this.briefJson = briefJson;
        this.briefModel = model;
        this.status = IntakeStatus.BRIEF_READY;
        touch();
    }

    @PrePersist
    void onCreate() {
        Instant now = now();
        createdAt = now;
        updatedAt = now;
    }

    private void touch() {
        updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getIdea() {
        return idea;
    }

    public IntakeStatus getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public String getBriefJson() {
        return briefJson;
    }

    public String getBriefModel() {
        return briefModel;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
