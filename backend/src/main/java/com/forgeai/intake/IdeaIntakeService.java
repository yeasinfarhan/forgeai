package com.forgeai.intake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.forgeai.intake.IdeaIntakeAssistant.AnsweredRound;
import com.forgeai.intake.IdeaIntakeAssistant.QuestionAndAnswer;
import com.forgeai.intake.IntakeStore.Snapshot;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;

/**
 * Idea intake use cases: describe the idea, answer Claude's clarifying questions (up to
 * {@value #MAX_QUESTION_ROUNDS} rounds), and get a {@link ProjectBrief}.
 *
 * <p>Not transactional on purpose: Claude calls can take tens of seconds. Reads and writes go
 * through {@link IntakeStore} in short transactions, with an optimistic version check so two
 * concurrent submissions cannot both apply.
 */
@Service
public class IdeaIntakeService {

    public static final int MAX_QUESTION_ROUNDS = 3;

    private final ProjectService projects;
    private final IntakeStore store;
    private final IdeaIntakeAssistant assistant;

    IdeaIntakeService(ProjectService projects, IntakeStore store, IdeaIntakeAssistant assistant) {
        this.projects = projects;
        this.store = store;
        this.assistant = assistant;
    }

    public IntakeResponse get(UUID ownerId, UUID projectId) {
        projects.get(ownerId, projectId); // ownership check: 404 for other users' projects
        return store.load(projectId).map(this::toResponse).orElseGet(IntakeResponse::notStarted);
    }

    /** Starts (or restarts) intake with a new idea description. */
    public IntakeResponse start(UUID ownerId, UUID projectId, String idea) {
        ProjectResponse project = projects.get(ownerId, projectId);
        String cleanIdea = idea.strip();

        AssistantTurn turn = assistant.nextTurn(
                new IdeaIntakeAssistant.Context(project.name(), project.description(), cleanIdea, List.of(), MAX_QUESTION_ROUNDS),
                false);
        return toResponse(store.start(projectId, cleanIdea, turn));
    }

    /**
     * Records answers to the current round and lets Claude ask more or write the brief.
     *
     * @param forceBrief write the brief now, even if Claude would ask more (also regenerates an existing brief)
     */
    public IntakeResponse answer(UUID ownerId, UUID projectId, Map<UUID, String> rawAnswers, boolean forceBrief) {
        ProjectResponse project = projects.get(ownerId, projectId);
        Snapshot snapshot = store.load(projectId)
                .orElseThrow(() -> new IntakeStateException("Describe your idea first."));
        IdeaIntake intake = snapshot.intake();

        if (intake.getStatus() == IntakeStatus.BRIEF_READY && !forceBrief) {
            throw new IntakeStateException("The brief has already been written. Start over to change the idea.");
        }

        Map<UUID, String> answers = normalize(rawAnswers);
        if (intake.getStatus() == IntakeStatus.AWAITING_ANSWERS) {
            Set<UUID> currentIds = snapshot.questions().stream()
                    .filter(q -> q.getRoundNumber() == intake.getCurrentRound())
                    .map(IntakeQuestion::getId)
                    .collect(Collectors.toSet());
            if (!currentIds.containsAll(answers.keySet())) {
                throw new IntakeStateException("These questions are no longer current. Reload the page.");
            }
        } else if (!answers.isEmpty()) {
            throw new IntakeStateException("There are no open questions to answer.");
        }

        int roundsAsked = (int) snapshot.questions().stream().map(IntakeQuestion::getRoundNumber).distinct().count();
        IdeaIntakeAssistant.Context context = new IdeaIntakeAssistant.Context(
                project.name(),
                project.description(),
                intake.getIdea(),
                answeredRounds(snapshot, answers),
                MAX_QUESTION_ROUNDS - roundsAsked);

        AssistantTurn turn = assistant.nextTurn(context, forceBrief);
        return toResponse(store.advance(projectId, intake.getVersion(), answers, turn));
    }

    private static Map<UUID, String> normalize(Map<UUID, String> rawAnswers) {
        Map<UUID, String> answers = new HashMap<>();
        rawAnswers.forEach((id, answer) -> {
            if (answer != null && !answer.isBlank()) {
                answers.put(id, answer.strip());
            }
        });
        return answers;
    }

    /** All rounds so far, with the new (not yet saved) answers applied to the current round. */
    private static List<AnsweredRound> answeredRounds(Snapshot snapshot, Map<UUID, String> newAnswers) {
        boolean awaiting = snapshot.intake().getStatus() == IntakeStatus.AWAITING_ANSWERS;
        int currentRound = snapshot.intake().getCurrentRound();

        Map<Integer, List<QuestionAndAnswer>> byRound = new LinkedHashMap<>();
        for (IntakeQuestion q : snapshot.questions()) {
            String answer = awaiting && q.getRoundNumber() == currentRound ? newAnswers.get(q.getId()) : q.getAnswer();
            byRound.computeIfAbsent(q.getRoundNumber(), round -> new ArrayList<>())
                    .add(new QuestionAndAnswer(q.getQuestion(), answer));
        }
        return byRound.entrySet().stream()
                .map(entry -> new AnsweredRound(entry.getKey(), entry.getValue()))
                .toList();
    }

    private IntakeResponse toResponse(Snapshot snapshot) {
        IdeaIntake intake = snapshot.intake();
        Map<Integer, List<IntakeResponse.Question>> byRound = new LinkedHashMap<>();
        for (IntakeQuestion q : snapshot.questions()) {
            byRound.computeIfAbsent(q.getRoundNumber(), round -> new ArrayList<>())
                    .add(new IntakeResponse.Question(q.getId(), q.getQuestion(), q.getRationale(), q.getAnswer()));
        }
        List<IntakeResponse.Round> rounds = byRound.entrySet().stream()
                .map(entry -> new IntakeResponse.Round(entry.getKey(), entry.getValue()))
                .toList();

        return new IntakeResponse(
                intake.getStatus(),
                intake.getIdea(),
                intake.getStatus() == IntakeStatus.AWAITING_ANSWERS ? intake.getCurrentRound() : null,
                MAX_QUESTION_ROUNDS,
                rounds,
                store.readBrief(intake),
                intake.getBriefModel(),
                intake.getUpdatedAt());
    }
}
