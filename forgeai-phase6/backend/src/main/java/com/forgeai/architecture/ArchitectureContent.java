package com.forgeai.architecture;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The architecture proposal of a project. Stored as JSON, sent to and from the client as is.
 * Bean validation covers shape and sizes; cross-references (component names, requirement codes)
 * are checked by {@link ArchitectureRules}.
 *
 * @param overview       how the system works as a whole, in a few paragraphs
 * @param style          the architectural style, e.g. "Modular monolith"
 * @param styleRationale why that style fits this project
 * @param components     the building blocks; the component diagram is derived from them
 * @param decisions      architecture decision records (technology choices and trade-offs)
 * @param flows          key interactions, each with a Mermaid sequence diagram
 */
public record ArchitectureContent(
        @NotBlank @Size(max = 6_000) String overview,
        @NotBlank @Size(max = 100) String style,
        @NotBlank @Size(max = 2_000) String styleRationale,
        @NotNull @Size(min = 1, max = ArchitectureContent.MAX_COMPONENTS) List<@NotNull @Valid Component> components,
        @NotNull @Size(max = ArchitectureContent.MAX_DECISIONS) List<@NotNull @Valid Decision> decisions,
        @NotNull @Size(max = ArchitectureContent.MAX_FLOWS) List<@NotNull @Valid Flow> flows) {

    public static final int MAX_COMPONENTS = 30;
    public static final int MAX_DECISIONS = 20;
    public static final int MAX_FLOWS = 6;

    /** Plain-text rendering for AI prompts of later phases (diagrams left out). */
    public String toPromptText() {
        StringBuilder text = new StringBuilder();
        text.append("Style: ").append(style).append(" (").append(styleRationale).append(")\n");
        text.append("Overview: ").append(overview).append("\n");
        text.append("Components:\n");
        for (Component c : components) {
            text.append("- ").append(c.name()).append(" [").append(c.kind());
            if (c.technology() != null) text.append(", ").append(c.technology());
            text.append("]: ").append(c.responsibility());
            if (!c.dependsOn().isEmpty()) text.append(" Depends on: ").append(String.join(", ", c.dependsOn())).append('.');
            text.append('\n');
        }
        if (!decisions.isEmpty()) {
            text.append("Decisions:\n");
            for (Decision d : decisions) {
                text.append("- ").append(d.title()).append(": ").append(d.decision()).append('\n');
            }
        }
        return text.toString().strip();
    }

    /**
     * @param dependsOn    names of the components this one calls or reads from
     * @param requirements codes of the requirements this component helps satisfy (e.g. FR-001)
     */
    public record Component(
            @NotBlank @Size(max = 80) String name,
            @NotNull ComponentKind kind,
            @NotBlank @Size(max = 1_000) String responsibility,
            @Size(max = 200) String technology,
            @NotNull @Size(max = MAX_COMPONENTS) List<@NotBlank @Size(max = 80) String> dependsOn,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 20) String> requirements) {
    }

    /** An architecture decision record: the choice, the alternatives and the consequences. */
    public record Decision(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 3_000) String context,
            @NotBlank @Size(max = 3_000) String decision,
            @NotNull @Size(max = 8) List<@NotBlank @Size(max = 500) String> alternatives,
            @NotBlank @Size(max = 3_000) String consequences,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 20) String> requirements) {
    }

    /** @param diagram Mermaid source, normally a {@code sequenceDiagram} */
    public record Flow(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1_000) String description,
            @NotBlank @Size(max = 10_000) String diagram) {
    }
}
