package com.forgeai.intake;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Database side of idea intake. Each method is one short transaction; the (slow) Claude call
 * happens between them in {@link IdeaIntakeService}, so no connection is held while waiting.
 */
@Component
class IntakeStore {

    record Snapshot(IdeaIntake intake, List<IntakeQuestion> questions) {
    }

    private final IdeaIntakeRepository intakes;
    private final IntakeQuestionRepository questions;
    private final JsonMapper jsonMapper;

    IntakeStore(IdeaIntakeRepository intakes, IntakeQuestionRepository questions, JsonMapper jsonMapper) {
        this.intakes = intakes;
        this.questions = questions;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    Optional<Snapshot> load(UUID projectId) {
        return intakes.findById(projectId)
                .map(intake -> new Snapshot(intake, questions.findAllByProjectIdOrderByRoundNumberAscPositionIndexAsc(projectId)));
    }

    /** Replaces any previous intake of the project with a new one built from Claude's first turn. */
    @Transactional
    Snapshot start(UUID projectId, String idea, AssistantTurn turn) {
        questions.deleteByProjectId(projectId);
        intakes.deleteByProjectId(projectId);

        IdeaIntake intake = switch (turn) {
            case AssistantTurn.Questions q -> IdeaIntake.awaitingAnswers(projectId, idea);
            case AssistantTurn.Brief b -> IdeaIntake.withBrief(projectId, idea, toJson(b.brief()), b.model());
        };
        intakes.save(intake);
        if (turn instanceof AssistantTurn.Questions q) {
            addRound(projectId, intake.getCurrentRound(), q.questions());
        }
        return reload(projectId);
    }

    /**
     * Saves the answers for the current round and applies Claude's next turn.
     *
     * @throws IntakeStateException if the intake changed since {@code expectedVersion} was read
     */
    @Transactional
    Snapshot advance(UUID projectId, int expectedVersion, Map<UUID, String> answers, AssistantTurn turn) {
        IdeaIntake intake = intakes.findById(projectId)
                .orElseThrow(() -> new IntakeStateException("This intake no longer exists. Reload the page."));
        if (intake.getVersion() != expectedVersion) {
            throw new IntakeStateException("This intake was changed in another tab or window. Reload to see the latest.");
        }

        if (intake.getStatus() == IntakeStatus.AWAITING_ANSWERS) {
            for (IntakeQuestion question : questions.findAllByProjectIdOrderByRoundNumberAscPositionIndexAsc(projectId)) {
                if (question.getRoundNumber() == intake.getCurrentRound()) {
                    question.answer(answers.get(question.getId()));
                }
            }
        }

        switch (turn) {
            case AssistantTurn.Questions q -> {
                intake.startNextRound();
                addRound(projectId, intake.getCurrentRound(), q.questions());
            }
            case AssistantTurn.Brief b -> intake.completeWith(toJson(b.brief()), b.model());
        }
        return reload(projectId);
    }

    ProjectBrief readBrief(IdeaIntake intake) {
        return intake.getBriefJson() == null ? null : jsonMapper.readValue(intake.getBriefJson(), ProjectBrief.class);
    }

    private void addRound(UUID projectId, int round, List<AssistantTurn.ClarifyingQuestion> newQuestions) {
        for (int i = 0; i < newQuestions.size(); i++) {
            AssistantTurn.ClarifyingQuestion q = newQuestions.get(i);
            questions.save(new IntakeQuestion(projectId, round, i + 1, q.question(), q.rationale()));
        }
    }

    private Snapshot reload(UUID projectId) {
        IdeaIntake intake = intakes.findById(projectId).orElseThrow();
        return new Snapshot(intake, questions.findAllByProjectIdOrderByRoundNumberAscPositionIndexAsc(projectId));
    }

    private String toJson(ProjectBrief brief) {
        return jsonMapper.writeValueAsString(brief);
    }
}
