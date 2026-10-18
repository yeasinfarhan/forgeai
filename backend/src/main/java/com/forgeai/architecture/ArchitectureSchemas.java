package com.forgeai.architecture;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** JSON schema for a generated architecture (structured outputs; converted for Gemini automatically). */
final class ArchitectureSchemas {

    private ArchitectureSchemas() {
    }

    private static final Map<String, Object> REQUIREMENT_CODES = Map.of(
            "type", "array",
            "description", "Codes of the requirements this addresses, exactly as given (e.g. FR-001, NFR-002).",
            "items", Map.of("type", "string"));

    private static final Map<String, Object> COMPONENT = Map.of(
            "type", "object",
            "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Short unique name, e.g. \"Web app\", \"Invoice API\", \"PostgreSQL\"."),
                    "kind", Map.of("type", "string", "enum", Arrays.stream(ComponentKind.values()).map(Enum::name).toList()),
                    "responsibility", Map.of("type", "string", "description", "What it does and owns, in one to three sentences."),
                    "technology", Map.of("type", "string", "description", "Concrete technology, e.g. \"React + TypeScript\"."),
                    "dependsOn", Map.of("type", "array",
                            "description", "Names of other components this one calls or reads from.",
                            "items", Map.of("type", "string")),
                    "requirements", REQUIREMENT_CODES),
            "required", List.of("name", "kind", "responsibility", "technology", "dependsOn", "requirements"),
            "additionalProperties", false);

    private static final Map<String, Object> DECISION = Map.of(
            "type", "object",
            "properties", Map.of(
                    "title", Map.of("type", "string", "description", "The decision in a few words, e.g. \"Use PostgreSQL for all data\"."),
                    "context", Map.of("type", "string", "description", "The forces and requirements that make this decision necessary."),
                    "decision", Map.of("type", "string", "description", "What is decided."),
                    "alternatives", Map.of("type", "array",
                            "description", "Options considered and why they were not chosen.",
                            "items", Map.of("type", "string")),
                    "consequences", Map.of("type", "string", "description", "Trade-offs: what gets easier, what gets harder, risks."),
                    "requirements", REQUIREMENT_CODES),
            "required", List.of("title", "context", "decision", "alternatives", "consequences", "requirements"),
            "additionalProperties", false);

    private static final Map<String, Object> FLOW = Map.of(
            "type", "object",
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "description", Map.of("type", "string", "description", "One sentence on what the flow shows."),
                    "diagram", Map.of("type", "string", "description", "Mermaid sequenceDiagram source, without code fences.")),
            "required", List.of("title", "description", "diagram"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of(
                    "overview", Map.of("type", "string", "description", "How the system works as a whole, 1-3 short paragraphs."),
                    "style", Map.of("type", "string", "description", "Architectural style, e.g. \"Modular monolith\"."),
                    "styleRationale", Map.of("type", "string", "description", "Why this style fits the project."),
                    "components", Map.of("type", "array", "items", COMPONENT),
                    "decisions", Map.of("type", "array", "items", DECISION),
                    "flows", Map.of("type", "array", "items", FLOW)),
            "required", List.of("overview", "style", "styleRationale", "components", "decisions", "flows"),
            "additionalProperties", false);
}
