package com.forgeai.intake;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the intake prompts, asks the model for the next step and turns its JSON answer into an
 * {@link AssistantTurn}. The model's output is validated here because the schema cannot express
 * every rule (e.g. "at most five questions").
 */
@Component
class IdeaIntakeAssistant {

    static final int MAX_QUESTIONS_PER_ROUND = 5;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    IdeaIntakeAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    /** What the assistant knows when deciding the next step. */
    record Context(
            String projectName,
            String projectDescription,
            String idea,
            List<AnsweredRound> rounds,
            int questionRoundsRemaining) {
    }

    record AnsweredRound(int number, List<QuestionAndAnswer> items) {
    }

    record QuestionAndAnswer(String question, String answer) {
    }

    /**
     * Asks the model for the next step. With {@code forceBrief}, or when no question rounds remain,
     * the model must write the brief.
     */
    AssistantTurn nextTurn(Context context, boolean forceBrief) {
        boolean briefOnly = forceBrief || context.questionRoundsRemaining() <= 0;

        String system = prompts.render("idea-intake-system", Map.of("maxQuestionsPerRound", MAX_QUESTIONS_PER_ROUND));
        String user = prompts.render("idea-intake-turn", Map.of(
                "projectName", context.projectName(),
                "projectDescription", orNone(context.projectDescription()),
                "idea", context.idea(),
                "clarifications", renderClarifications(context.rounds()),
                "instruction", briefOnly
                        ? "Write the project brief now. Do not ask more questions; list anything still unknown under openQuestions."
                        : "Decide the next step. You may ask up to " + MAX_QUESTIONS_PER_ROUND + " questions in this round. "
                                + "Question rounds remaining after this one: " + (context.questionRoundsRemaining() - 1) + "."));

        if (briefOnly) {
            AiResult result = ai.complete(new AiRequest("idea-intake.brief", system, user, IntakeSchemas.BRIEF));
            ProjectBrief brief = parse(result.text(), ProjectBrief.class);
            return new AssistantTurn.Brief(validate(brief), result.model());
        }

        AiResult result = ai.complete(new AiRequest("idea-intake.turn", system, user, IntakeSchemas.TURN));
        TurnPayload payload = parse(result.text(), TurnPayload.class);

        if (IntakeSchemas.WRITE_BRIEF.equals(payload.decision())) {
            return new AssistantTurn.Brief(validate(payload.brief()), result.model());
        }
        List<AssistantTurn.ClarifyingQuestion> questions = payload.questions() == null ? List.of()
                : payload.questions().stream()
                        .filter(q -> q.question() != null && !q.question().isBlank())
                        .limit(MAX_QUESTIONS_PER_ROUND)
                        .map(q -> new AssistantTurn.ClarifyingQuestion(q.question().strip(), nullToEmpty(q.rationale()).strip()))
                        .toList();
        if (questions.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model chose ask_questions but returned no questions");
        }
        return new AssistantTurn.Questions(questions, result.model());
    }

    /** JSON shape of a normal turn, matching {@link IntakeSchemas#TURN}. */
    record TurnPayload(String decision, List<AssistantTurn.ClarifyingQuestion> questions, ProjectBrief brief) {
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match " + type.getSimpleName(), ex);
        }
    }

    private static ProjectBrief validate(ProjectBrief brief) {
        if (brief == null || isBlank(brief.title()) || isBlank(brief.summary())
                || brief.coreFeatures() == null || brief.coreFeatures().isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an incomplete brief");
        }
        return new ProjectBrief(
                brief.title().strip(),
                brief.summary().strip(),
                nullToEmpty(brief.problem()).strip(),
                orEmpty(brief.targetUsers()),
                orEmpty(brief.goals()),
                brief.coreFeatures(),
                orEmpty(brief.outOfScope()),
                orEmpty(brief.constraints()),
                orEmpty(brief.openQuestions()));
    }

    private static String renderClarifications(List<AnsweredRound> rounds) {
        if (rounds.isEmpty()) {
            return "(none yet)";
        }
        StringBuilder text = new StringBuilder();
        for (AnsweredRound round : rounds) {
            text.append("Round ").append(round.number()).append(":\n");
            for (QuestionAndAnswer item : round.items()) {
                text.append("Q: ").append(item.question()).append('\n');
                text.append("A: ").append(isBlank(item.answer()) ? "(skipped)" : item.answer().strip()).append('\n');
            }
            text.append('\n');
        }
        return text.toString().strip();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String orNone(String text) {
        return isBlank(text) ? "(none)" : text;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
