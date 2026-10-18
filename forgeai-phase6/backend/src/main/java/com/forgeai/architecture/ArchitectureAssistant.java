package com.forgeai.architecture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.architecture.ArchitectureContent.Decision;
import com.forgeai.architecture.ArchitectureContent.Flow;
import com.forgeai.intake.ProjectBrief;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI for an architecture proposal and repairs what comes back so it always satisfies the
 * same rules as a person's edit: sizes, unique component names, dependencies between real
 * components, and only codes of approved requirements.
 */
@Component
class ArchitectureAssistant {

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    ArchitectureAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    record Generated(ArchitectureContent content, String model) {
    }

    Generated generate(String projectName, ProjectBrief brief, List<RequirementResponse> approved) {
        String user = prompts.render("architecture-generate", Map.of(
                "projectName", projectName,
                "brief", brief == null ? "(no brief; work from the requirements)" : brief.toPromptText(),
                "requirements", renderRequirements(approved)));
        String system = prompts.render("architecture-system", Map.of());

        AiResult result = ai.complete(new AiRequest("architecture.generate", system, user, ArchitectureSchemas.GENERATED));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the architecture schema", ex);
        }
        Set<String> codes = approved.stream().map(r -> r.code().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return new Generated(repair(payload, codes), result.model());
    }

    // --- Model output ------------------------------------------------------------

    /** JSON shape matching {@link ArchitectureSchemas#GENERATED}; everything optional until repaired. */
    record Payload(
            String overview,
            String style,
            String styleRationale,
            List<ComponentItem> components,
            List<DecisionItem> decisions,
            List<FlowItem> flows) {
    }

    record ComponentItem(String name, String kind, String responsibility, String technology, List<String> dependsOn, List<String> requirements) {
    }

    record DecisionItem(String title, String context, String decision, List<String> alternatives, String consequences, List<String> requirements) {
    }

    record FlowItem(String title, String description, String diagram) {
    }

    static ArchitectureContent repair(Payload payload, Set<String> approvedCodes) {
        if (isBlank(payload.overview()) || isBlank(payload.style()) || isBlank(payload.styleRationale())) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an architecture without overview or style");
        }

        // Components first: dependencies can only point at components that survived.
        Map<String, ComponentItem> byKey = new LinkedHashMap<>();
        for (ComponentItem item : nonNull(payload.components())) {
            if (item == null || isBlank(item.name()) || isBlank(item.responsibility())) continue;
            if (byKey.size() == ArchitectureContent.MAX_COMPONENTS) break;
            byKey.putIfAbsent(ArchitectureRules.key(truncate(item.name(), 80)), item);
        }
        if (byKey.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an architecture without components");
        }
        Map<String, String> canonicalNames = new LinkedHashMap<>();
        byKey.forEach((key, item) -> canonicalNames.put(key, truncate(item.name(), 80)));

        List<ArchitectureContent.Component> components = new ArrayList<>();
        byKey.forEach((key, item) -> components.add(new ArchitectureContent.Component(
                canonicalNames.get(key),
                parseKind(item.kind()),
                truncate(item.responsibility(), 1_000),
                truncate(item.technology(), 200),
                nonNull(item.dependsOn()).stream()
                        .filter(Objects::nonNull)
                        .map(ArchitectureRules::key)
                        .filter(dependency -> !dependency.equals(key) && canonicalNames.containsKey(dependency))
                        .distinct()
                        .map(canonicalNames::get)
                        .toList(),
                codes(item.requirements(), approvedCodes))));

        List<Decision> decisions = nonNull(payload.decisions()).stream()
                .filter(d -> d != null && !isBlank(d.title()) && !isBlank(d.context()) && !isBlank(d.decision())
                        && !isBlank(d.consequences()))
                .limit(ArchitectureContent.MAX_DECISIONS)
                .map(d -> new Decision(
                        truncate(d.title(), 200),
                        truncate(d.context(), 3_000),
                        truncate(d.decision(), 3_000),
                        nonNull(d.alternatives()).stream().filter(a -> !isBlank(a)).limit(8).map(a -> truncate(a, 500)).toList(),
                        truncate(d.consequences(), 3_000),
                        codes(d.requirements(), approvedCodes)))
                .toList();

        List<Flow> flows = nonNull(payload.flows()).stream()
                .filter(f -> f != null && !isBlank(f.title()) && !isBlank(f.diagram())
                        && !ArchitectureRules.cleanDiagram(f.diagram()).isEmpty())
                .limit(ArchitectureContent.MAX_FLOWS)
                .map(f -> new Flow(
                        truncate(f.title(), 200),
                        truncate(f.description(), 1_000),
                        truncate(ArchitectureRules.cleanDiagram(f.diagram()), 10_000)))
                .toList();

        return ArchitectureRules.tidy(new ArchitectureContent(
                truncate(payload.overview(), 6_000),
                truncate(payload.style(), 100),
                truncate(payload.styleRationale(), 2_000),
                components,
                decisions,
                flows));
    }

    private static List<String> codes(List<String> codes, Set<String> approved) {
        return nonNull(codes).stream()
                .filter(Objects::nonNull)
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .filter(approved::contains)
                .distinct()
                .limit(100)
                .toList();
    }

    private static ComponentKind parseKind(String value) {
        if (value == null) return ComponentKind.SERVICE;
        try {
            return ComponentKind.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return ComponentKind.SERVICE;
        }
    }

    // --- Rendering -------------------------------------------------------------

    static String renderRequirements(List<RequirementResponse> approved) {
        StringBuilder text = new StringBuilder();
        for (RequirementResponse r : approved) {
            text.append(r.code()).append(" [").append(r.priority());
            if (!isBlank(r.category())) text.append(", ").append(r.category());
            text.append("] ").append(r.title()).append(": ").append(r.description()).append('\n');
            if (!r.acceptanceCriteria().isEmpty()) {
                text.append("  Acceptance: ").append(String.join("; ", r.acceptanceCriteria())).append('\n');
            }
        }
        return text.toString().strip();
    }

    // --- Helpers -----------------------------------------------------------------

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
