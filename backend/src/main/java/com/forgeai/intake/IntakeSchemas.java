package com.forgeai.intake;

import java.util.List;
import java.util.Map;

/**
 * JSON schemas Claude's answers must match (structured outputs). Kept within the subset the API
 * supports: every object lists all properties as required and sets additionalProperties=false;
 * no length or item-count limits (those are enforced in {@link IdeaIntakeAssistant} instead).
 */
final class IntakeSchemas {

    static final String ASK_QUESTIONS = "ask_questions";
    static final String WRITE_BRIEF = "write_brief";

    private IntakeSchemas() {
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> stringList(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string"));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.copyOf(properties.keySet()),
                "additionalProperties", false);
    }

    static final Map<String, Object> QUESTION = object(Map.of(
            "question", string("One clear question for the user, in plain language."),
            "rationale", string("One short sentence: why the answer matters for building this software.")));

    static final Map<String, Object> FEATURE = object(Map.of(
            "name", string("Short feature name."),
            "description", string("What the feature lets users do, in one or two sentences.")));

    static final Map<String, Object> BRIEF = object(Map.of(
            "title", string("Working title for the software."),
            "summary", string("Two or three sentences describing the product."),
            "problem", string("The problem it solves and for whom."),
            "targetUsers", stringList("Distinct user groups."),
            "goals", stringList("Measurable or observable outcomes the product should achieve."),
            "coreFeatures", Map.of("type", "array", "description", "Capabilities for the first version.", "items", FEATURE),
            "outOfScope", stringList("Things explicitly not in the first version."),
            "constraints", stringList("Technical, legal, budget or time constraints the user stated."),
            "openQuestions", stringList("Important things still unknown. Never invent answers to these.")));

    /** A normal turn: Claude chooses between asking more questions and writing the brief. */
    static final Map<String, Object> TURN = object(Map.of(
            "decision", Map.of("type", "string", "enum", List.of(ASK_QUESTIONS, WRITE_BRIEF)),
            "questions", Map.of(
                    "type", "array",
                    "description", "When decision is ask_questions: the questions (at most the allowed number). Otherwise empty.",
                    "items", QUESTION),
            "brief", Map.of(
                    "description", "When decision is write_brief: the brief. Otherwise null.",
                    "anyOf", List.of(BRIEF, Map.of("type", "null")))));
}
