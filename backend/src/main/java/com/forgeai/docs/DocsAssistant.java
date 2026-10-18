package com.forgeai.docs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.code.CodePaths;
import com.forgeai.code.CodeService.SourceFile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI to write the README or the developer guide from the approved design and the
 * accepted code, and tidies the answer (a Markdown fence around the whole document is removed).
 */
@Component
class DocsAssistant {

    static final int MAX_OUTPUT_TOKENS = 16_000;
    static final int MAX_CHARS = 100_000;
    /** How much code is shown to the model, in characters. */
    static final int CODE_BUDGET = 80_000;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("content", Map.of("type", "string", "description", "The complete document as GitHub-flavoured Markdown.")),
            "required", List.of("content"),
            "additionalProperties", false);

    /** Files that say how the project is built, configured and run; shown before other code. */
    private static final Set<String> SETUP_FILES = Set.of(
            "dockerfile", "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml", ".env.example",
            "makefile", "application.yml", "application.yaml", "application.properties", "vite.config.ts", "tsconfig.json");

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    DocsAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    record Written(String content, String model) {
    }

    /**
     * @param design    the approved design as prompt text (project, requirements, architecture, database, api)
     * @param documents the paths of the other documents in the package, so the document can link to them
     */
    Written write(DocumentKind kind, Map<String, String> design, List<String> documents, List<SourceFile> files) {
        Shown shown = shown(files);
        Map<String, String> values = new java.util.HashMap<>(design);
        values.put("documents", String.join("\n", documents));
        values.put("workspace", shown.listing());
        values.put("files", shown.contents());
        String user = prompts.render(kind.prompt(), values);
        AiResult result = ai.complete(new AiRequest("docs." + kind.name().toLowerCase(Locale.ROOT),
                prompts.render("docs-system", Map.of()), user, SCHEMA, MAX_OUTPUT_TOKENS));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the document schema", ex);
        }
        String content = tidy(payload.content());
        if (content == null) throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an empty " + kind.title());
        return new Written(content, result.model());
    }

    record Payload(String content) {
    }

    /** The document without a wrapping code fence, ending with a newline; null when empty. */
    static String tidy(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.strip();
        if (text.startsWith("```") && text.endsWith("```") && text.length() > 6) {
            int firstNewline = text.indexOf('\n');
            String fence = firstNewline > 0 ? text.substring(3, firstNewline).strip().toLowerCase(Locale.ROOT) : "";
            if (firstNewline > 0 && (fence.isEmpty() || fence.equals("markdown") || fence.equals("md"))) {
                text = text.substring(firstNewline + 1, text.length() - 3).strip();
            }
        }
        if (text.isEmpty()) return null;
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS).strip();
        return text + "\n";
    }

    record Shown(String listing, String contents) {
    }

    /** Lists every file; shows setup and build files first, then application code, then tests, within the budget. */
    static Shown shown(List<SourceFile> files) {
        if (files.isEmpty()) return new Shown("(empty: no code has been accepted yet)", "(none)");
        List<SourceFile> ordered = new ArrayList<>(files);
        ordered.sort(Comparator
                .comparingInt((SourceFile f) -> rank(f.path()))
                .thenComparing(SourceFile::updatedAt, Comparator.reverseOrder()));
        Set<String> included = new HashSet<>();
        StringBuilder contents = new StringBuilder();
        int used = 0;
        for (SourceFile file : ordered) {
            int size = file.content().length() + file.path().length() + 10;
            if (used + size > CODE_BUDGET) continue;
            contents.append("=== ").append(file.path()).append(" ===\n").append(file.content());
            if (!file.content().endsWith("\n")) contents.append('\n');
            contents.append('\n');
            used += size;
            included.add(file.path());
        }
        StringBuilder listing = new StringBuilder();
        for (SourceFile file : files) {
            listing.append(file.path());
            if (!included.contains(file.path())) listing.append(" (content not shown)");
            listing.append('\n');
        }
        return new Shown(listing.toString().strip(), contents.isEmpty() ? "(none)" : contents.toString().strip());
    }

    private static int rank(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (CodePaths.isBuildFile(path) || SETUP_FILES.contains(name)) return 0;
        if (CodePaths.isTestFile(path)) return 2;
        return 1;
    }
}
