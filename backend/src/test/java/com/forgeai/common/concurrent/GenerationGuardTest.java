package com.forgeai.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.forgeai.common.exception.ConflictException;

class GenerationGuardTest {

    private final GenerationGuard guard = new GenerationGuard();
    private final UUID project = UUID.randomUUID();

    @Test
    void refusesASecondRunOfTheSameStageWhileTheFirstIsRunning() {
        String result = guard.run(project, "api-design", "API design", () -> {
            assertThatThrownBy(() -> guard.run(project, "api-design", "API design", () -> "second"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("The API design is already being generated. Wait until it finishes, then reload the page.");
            // Other stages and other projects are not blocked.
            assertThat(guard.run(project, "tasks", "backlog", () -> "tasks")).isEqualTo("tasks");
            assertThat(guard.run(UUID.randomUUID(), "api-design", "API design", () -> "other")).isEqualTo("other");
            return "first";
        });
        assertThat(result).isEqualTo("first");
    }

    @Test
    void releasesTheStageWhenTheRunFails() {
        assertThatThrownBy(() -> guard.run(project, "api-design", "API design", () -> {
            throw new IllegalStateException("AI failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(guard.run(project, "api-design", "API design", () -> "again")).isEqualTo("again");
    }
}
