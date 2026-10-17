package com.forgeai.intake;

import java.util.List;

/** What Claude decided on one intake turn: either more questions, or the finished brief. */
sealed interface AssistantTurn {

    String model();

    record Questions(List<ClarifyingQuestion> questions, String model) implements AssistantTurn {
    }

    record Brief(ProjectBrief brief, String model) implements AssistantTurn {
    }

    record ClarifyingQuestion(String question, String rationale) {
    }
}
