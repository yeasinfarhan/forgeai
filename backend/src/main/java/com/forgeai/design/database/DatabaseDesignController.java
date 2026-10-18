package com.forgeai.design.database;

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
import com.forgeai.design.DesignExport;
import com.forgeai.design.database.DatabaseDtos.DatabaseDesignOverview;
import com.forgeai.design.database.DatabaseDtos.UpdateDatabaseDesign;

@RestController
@RequestMapping("/api/projects/{projectId}/database-design")
public class DatabaseDesignController {

    private final DatabaseDesignService designs;

    public DatabaseDesignController(DatabaseDesignService designs) {
        this.designs = designs;
    }

    @GetMapping
    public DatabaseDesignOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.overview(CurrentUser.id(jwt), projectId);
    }

    /** Generates (or regenerates, while in draft) the data model; calls the AI and can take up to a minute. */
    @PostMapping("/generate")
    public DatabaseDesignOverview generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.generate(CurrentUser.id(jwt), projectId);
    }

    @PutMapping
    public DatabaseDesignOverview update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody UpdateDatabaseDesign request) {
        return designs.update(CurrentUser.id(jwt), projectId, request);
    }

    @PostMapping("/status")
    public DatabaseDesignOverview changeStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody ChangeDesignStatus request) {
        return designs.changeStatus(CurrentUser.id(jwt), projectId, request);
    }

    @GetMapping("/revisions")
    public List<RevisionSummary> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return designs.history(CurrentUser.id(jwt), projectId);
    }

    @GetMapping("/revisions/{revision}")
    public RevisionDetail<DatabaseDesignContent> revision(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable int revision) {
        return designs.revision(CurrentUser.id(jwt), projectId, revision);
    }

    /** The data model as a PostgreSQL schema script (download). */
    @GetMapping("/export.sql")
    public ResponseEntity<String> exportSql(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return download(designs.exportSql(CurrentUser.id(jwt), projectId));
    }

    static ResponseEntity<String> download(DesignExport export) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType() + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.body());
    }
}
