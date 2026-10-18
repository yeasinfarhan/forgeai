package com.forgeai.docs;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.docs.DocsDtos.DocsOverview;
import com.forgeai.docs.DocsDtos.DocumentResponse;
import com.forgeai.docs.DocsDtos.PackageFile;
import com.forgeai.docs.DocsDtos.UpdateDocument;

@RestController
@RequestMapping("/api/projects/{projectId}/docs")
public class DocsController {

    private final DocsService docs;
    private final DocsGenerationService generation;

    public DocsController(DocsService docs, DocsGenerationService generation) {
        this.docs = docs;
        this.generation = generation;
    }

    @GetMapping
    public DocsOverview overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return docs.overview(CurrentUser.id(jwt), projectId);
    }

    @GetMapping("/documents/{kind}")
    public DocumentResponse document(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable DocumentKind kind) {
        return docs.document(CurrentUser.id(jwt), projectId, kind);
    }

    /** Writes (or rewrites) the document with the AI; can take a few minutes. */
    @PostMapping("/documents/{kind}/generate")
    public DocumentResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable DocumentKind kind) {
        return generation.generate(CurrentUser.id(jwt), projectId, kind);
    }

    @PutMapping("/documents/{kind}")
    public DocumentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @PathVariable DocumentKind kind,
            @Valid @RequestBody UpdateDocument request) {
        return docs.update(CurrentUser.id(jwt), projectId, kind, request);
    }

    /** One file of the project package (e.g. {@code ?path=docs/api-reference.md}). */
    @GetMapping("/package/file")
    public PackageFile packageFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam String path) {
        return docs.packageFile(CurrentUser.id(jwt), projectId, path);
    }

    /** The full project package as a ZIP archive (download). */
    @GetMapping("/package.zip")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        DocsService.PackageExport export = docs.export(CurrentUser.id(jwt), projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.bytes());
    }
}
