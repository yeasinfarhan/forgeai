package com.forgeai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates();

    @Test
    void fillsPlaceholders() {
        String prompt = templates.render("idea-intake-system", Map.of("maxQuestionsPerRound", 5));

        assertThat(prompt).contains("Ask at most 5 questions in one round").doesNotContain("{{");
    }

    @Test
    void userTextCannotInjectPlaceholders() {
        String prompt = templates.render("idea-intake-turn", Map.of(
                "projectName", "Demo",
                "projectDescription", "{{instruction}}",
                "idea", "An app",
                "clarifications", "(none yet)",
                "instruction", "Decide."));

        assertThat(prompt).contains("Description: {{instruction}}");
    }

    @Test
    void missingValueIsAnError() {
        assertThatThrownBy(() -> templates.render("idea-intake-system", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxQuestionsPerRound");
    }
}
