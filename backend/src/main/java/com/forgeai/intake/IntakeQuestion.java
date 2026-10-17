package com.forgeai.intake;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A clarifying question Claude asked during intake, and the user's answer once given. */
@Entity
@Table(name = "idea_intake_questions")
public class IntakeQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "round_number", nullable = false, updatable = false)
    private int roundNumber;

    @Column(name = "position_index", nullable = false, updatable = false)
    private int positionIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @Column(columnDefinition = "text")
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IntakeQuestion() {
        // Required by JPA
    }

    IntakeQuestion(UUID projectId, int roundNumber, int positionIndex, String question, String rationale) {
        this.projectId = projectId;
        this.roundNumber = roundNumber;
        this.positionIndex = positionIndex;
        this.question = question;
        this.rationale = rationale;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    void answer(String answer) {
        this.answer = answer;
    }

    public UUID getId() {
        return id;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public String getQuestion() {
        return question;
    }

    public String getRationale() {
        return rationale;
    }

    public String getAnswer() {
        return answer;
    }
}
