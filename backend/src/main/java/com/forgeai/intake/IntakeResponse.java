package com.forgeai.intake;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full state of a project's idea intake.
 *
 * @param currentRound the round whose questions are waiting for answers; null unless AWAITING_ANSWERS
 * @param brief        the finished brief; null unless BRIEF_READY
 */
public record IntakeResponse(
        IntakeStatus status,
        String idea,
        Integer currentRound,
        int maxQuestionRounds,
        List<Round> rounds,
        ProjectBrief brief,
        String briefModel,
        Instant updatedAt) {

    public record Round(int number, List<Question> questions) {
    }

    public record Question(UUID id, String question, String rationale, String answer) {
    }

    static IntakeResponse notStarted() {
        return new IntakeResponse(
                IntakeStatus.NOT_STARTED, null, null, IdeaIntakeService.MAX_QUESTION_ROUNDS, List.of(), null, null, null);
    }
}
