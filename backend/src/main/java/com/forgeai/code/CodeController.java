package com.forgeai.code;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.code.CodeDtos.ChangesetResponse;
import com.forgeai.code.CodeDtos.CodeOverview;
import com.forgeai.code.CodeDtos.FileContent;
import com.forgeai.common.security.CurrentUser;

@RestController
@RequestMapping("/api/projects/{projectId}/code")
public class CodeController {

    private final CodeService code;
    private final CodeGenerationService generation;

    public CodeController(CodeService code, CodeGenerationService generation) {
        this.code = code;
        this.generation = generation;
    }

    @GetMapping
    public CodeOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return code.overview(CurrentUser.id(jwt), projectId);
    }

    /** One accepted file, by path (e.g. {@code ?path=backend/pom.xml}). */
    @GetMapping("/file")
    public FileContent file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam String path) {
        return code.file(CurrentUser.id(jwt), projectId, path);
    }

    /** Generates the code for a task (calls the AI; can take a few minutes). */
    @PostMapping("/tasks/{taskId}/generate")
    public ChangesetResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID taskId) {
        return generation.generate(CurrentUser.id(jwt), projectId, taskId);
    }

    @GetMapping("/changesets/{changesetId}")
    public ChangesetResponse changeset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID changesetId) {
        return code.changeset(CurrentUser.id(jwt), projectId, changesetId);
    }

    @PostMapping("/changesets/{changesetId}/changes/{changeId}/accept")
    public ChangesetResponse accept(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID changesetId, @PathVariable UUID changeId) {
        return code.accept(CurrentUser.id(jwt), projectId, changesetId, changeId);
    }

    @PostMapping("/changesets/{changesetId}/changes/{changeId}/reject")
    public ChangesetResponse reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID changesetId, @PathVariable UUID changeId) {
        return code.reject(CurrentUser.id(jwt), projectId, changesetId, changeId);
    }

    @PostMapping("/changesets/{changesetId}/accept-all")
    public ChangesetResponse acceptAll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID changesetId) {
        return code.acceptAll(CurrentUser.id(jwt), projectId, changesetId);
    }

    @PostMapping("/changesets/{changesetId}/discard")
    public ChangesetResponse discard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID changesetId) {
        return code.discard(CurrentUser.id(jwt), projectId, changesetId);
    }

    /** The accepted code as a ZIP archive (download). */
    @GetMapping("/export.zip")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        CodeService.ZipExport export = code.export(CurrentUser.id(jwt), projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.bytes());
    }
}
