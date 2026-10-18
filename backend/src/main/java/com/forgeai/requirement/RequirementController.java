package com.forgeai.requirement;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.requirement.RequirementDtos.BulkChangeStatus;
import com.forgeai.requirement.RequirementDtos.ChangeStatus;
import com.forgeai.requirement.RequirementDtos.CreateRequirement;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementDtos.RequirementsOverview;
import com.forgeai.requirement.RequirementDtos.RevisionResponse;
import com.forgeai.requirement.RequirementDtos.UpdateRequirement;

@RestController
@RequestMapping("/api/projects/{projectId}/requirements")
public class RequirementController {

    private final RequirementService requirements;
    private final RequirementGenerationService generation;

    public RequirementController(RequirementService requirements, RequirementGenerationService generation) {
        this.requirements = requirements;
        this.generation = generation;
    }

    @GetMapping
    public RequirementsOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return requirements.overview(CurrentUser.id(jwt), projectId);
    }

    /** Generates requirements from the brief (calls the AI; can take up to a minute). */
    @PostMapping("/generate")
    public RequirementsOverview generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return generation.generate(CurrentUser.id(jwt), projectId);
    }

    @PostMapping
    public ResponseEntity<RequirementResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody CreateRequirement request) {
        RequirementResponse created = requirements.create(CurrentUser.id(jwt), projectId, request);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/requirements/" + created.id()))
                .body(created);
    }

    @PutMapping("/{requirementId}")
    public RequirementResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody UpdateRequirement request) {
        return requirements.update(CurrentUser.id(jwt), projectId, requirementId, request);
    }

    @PostMapping("/{requirementId}/status")
    public RequirementResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody ChangeStatus request) {
        return requirements.changeStatus(CurrentUser.id(jwt), projectId, requirementId, request);
    }

    @PostMapping("/bulk-status")
    public RequirementsOverview changeStatusInBulk(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody BulkChangeStatus request) {
        return requirements.changeStatusInBulk(CurrentUser.id(jwt), projectId, request);
    }

    @DeleteMapping("/{requirementId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID requirementId) {
        requirements.delete(CurrentUser.id(jwt), projectId, requirementId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{requirementId}/history")
    public List<RevisionResponse> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID requirementId) {
        return requirements.history(CurrentUser.id(jwt), projectId, requirementId);
    }
}
