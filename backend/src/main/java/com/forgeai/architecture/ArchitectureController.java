package com.forgeai.architecture;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.architecture.ArchitectureDtos.ArchitectureOverview;
import com.forgeai.architecture.ArchitectureDtos.ChangeArchitectureStatus;
import com.forgeai.architecture.ArchitectureDtos.RevisionDetail;
import com.forgeai.architecture.ArchitectureDtos.RevisionSummary;
import com.forgeai.architecture.ArchitectureDtos.UpdateArchitecture;
import com.forgeai.common.security.CurrentUser;

@RestController
@RequestMapping("/api/projects/{projectId}/architecture")
public class ArchitectureController {

    private final ArchitectureService architectures;
    private final ArchitectureGenerationService generation;

    public ArchitectureController(ArchitectureService architectures, ArchitectureGenerationService generation) {
        this.architectures = architectures;
        this.generation = generation;
    }

    @GetMapping
    public ArchitectureOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return architectures.overview(CurrentUser.id(jwt), projectId);
    }

    /** Generates (or regenerates, while in draft) the proposal; calls the AI and can take up to a minute. */
    @PostMapping("/generate")
    public ArchitectureOverview generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return generation.generate(CurrentUser.id(jwt), projectId);
    }

    @PutMapping
    public ArchitectureOverview update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody UpdateArchitecture request) {
        return architectures.update(CurrentUser.id(jwt), projectId, request);
    }

    @PostMapping("/status")
    public ArchitectureOverview changeStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody ChangeArchitectureStatus request) {
        return architectures.changeStatus(CurrentUser.id(jwt), projectId, request);
    }

    @GetMapping("/revisions")
    public List<RevisionSummary> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return architectures.history(CurrentUser.id(jwt), projectId);
    }

    @GetMapping("/revisions/{revision}")
    public RevisionDetail revision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable int revision) {
        return architectures.revision(CurrentUser.id(jwt), projectId, revision);
    }
}
