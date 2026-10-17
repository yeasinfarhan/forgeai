package com.forgeai.intake;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the idea intake endpoints. */
final class IntakeRequests {

    private IntakeRequests() {
    }

    record StartIntake(@NotBlank @Size(max = 10_000) String idea) {
    }

    record Answers(@NotNull @Size(max = 20) List<@Valid @NotNull Answer> answers) {

        Map<UUID, String> asMap() {
            Map<UUID, String> map = new HashMap<>();
            answers.forEach(item -> map.put(item.questionId(), item.answer()));
            return map;
        }
    }

    record Answer(@NotNull UUID questionId, @Size(max = 4_000) String answer) {
    }
}
