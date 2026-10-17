package com.forgeai.intake;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;

@RestController
@RequestMapping("/api/projects/{projectId}/intake")
public class IdeaIntakeController {

    private final IdeaIntakeService intakeService;

    public IdeaIntakeController(IdeaIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @GetMapping
    public IntakeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return intakeService.get(CurrentUser.id(jwt), projectId);
    }

    /** Starts intake with a description of the idea. Replaces any previous intake of the project. */
    @PostMapping
    public IntakeResponse start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody IntakeRequests.StartIntake request) {
        return intakeService.start(CurrentUser.id(jwt), projectId, request.idea());
    }

    /** Answers the current round of questions. Claude then asks more or writes the brief. */
    @PostMapping("/answers")
    public IntakeResponse answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody IntakeRequests.Answers request) {
        return intakeService.answer(CurrentUser.id(jwt), projectId, request.asMap(), false);
    }

    /** Writes (or rewrites) the brief now, optionally with answers to the current questions. */
    @PostMapping("/brief")
    public IntakeResponse writeBrief(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) IntakeRequests.Answers request) {
        Map<UUID, String> answers = request == null ? Map.of() : request.asMap();
        return intakeService.answer(CurrentUser.id(jwt), projectId, answers, true);
    }
}
