package com.forgeai.design.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.design.DesignDtos.ChangeDesignStatus;
import com.forgeai.design.DesignDtos.RevisionDetail;
import com.forgeai.design.DesignDtos.RevisionSummary;
import com.forgeai.design.api.ApiDtos.ApiDesignOverview;
import com.forgeai.design.api.ApiDtos.UpdateApiDesign;

@RestController
@RequestMapping("/api/projects/{projectId}/api-design")
public class ApiDesignController {

    private final ApiDesignService designs;

    public ApiDesignController(ApiDesignService designs) {
        this.designs = designs;
    }

    @GetMapping
    public ApiDesignOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.overview(CurrentUser.id(jwt), projectId);
    }

    /** Generates (or regenerates, while in draft) the API; calls the AI and can take up to a minute. */
    @PostMapping("/generate")
    public ApiDesignOverview generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.generate(CurrentUser.id(jwt), projectId);
    }

    @PutMapping
    public ApiDesignOverview update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody UpdateApiDesign request) {
        return designs.update(CurrentUser.id(jwt), projectId, request);
    }

    @PostMapping("/status")
    public ApiDesignOverview changeStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody ChangeDesignStatus request) {
        return designs.changeStatus(CurrentUser.id(jwt), projectId, request);
    }

    @GetMapping("/revisions")
    public List<RevisionSummary> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.history(CurrentUser.id(jwt), projectId);
    }

    @GetMapping("/revisions/{revision}")
    public RevisionDetail<ApiDesignContent> revision(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable int revision) {
        return designs.revision(CurrentUser.id(jwt), projectId, revision);
    }

    /** The API as an OpenAPI 3.1 document (download). */
    @GetMapping("/openapi.json")
    public ResponseEntity<String> exportOpenApi(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        var export = designs.exportOpenApi(CurrentUser.id(jwt), projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.body());
    }
}
