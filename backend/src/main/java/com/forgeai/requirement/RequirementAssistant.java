package com.forgeai.requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.intake.IntakeResponse;
import com.forgeai.intake.ProjectBrief;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI to derive requirements from the brief and validates what comes back. The schema
 * fixes the shape; limits the schema cannot express (lengths, counts) are enforced here.
 */
@Component
class RequirementAssistant {

    static final int MAX_REQUIREMENTS = 60;
    static final int MAX_CRITERIA = 10;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    RequirementAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    List<GeneratedRequirement> generate(IntakeResponse intake, List<Requirement> existing) {
        String user = prompts.render("requirements-generate", Map.of(
                "brief", renderBrief(intake.brief()),
                "clarifications", renderClarifications(intake.rounds()),
                "existingRequirements", renderExisting(existing)));
        String system = prompts.render("requirements-system", Map.of());

        AiResult result = ai.complete(new AiRequest("requirements.generate", system, user, RequirementSchemas.GENERATED));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the requirements schema", ex);
        }

        List<GeneratedRequirement> generated = new ArrayList<>();
        if (payload.requirements() != null) {
            for (Item item : payload.requirements()) {
                if (generated.size() == MAX_REQUIREMENTS) break;
                GeneratedRequirement requirement = toRequirement(item);
                if (requirement != null) generated.add(requirement);
            }
        }
        if (generated.isEmpty() && existing.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned no usable requirements");
        }
        return generated;
    }

    /** JSON shape matching {@link RequirementSchemas#GENERATED}. */
    record Payload(List<Item> requirements) {
    }

    record Item(String type, String category, String title, String description, List<String> acceptanceCriteria, String priority) {
    }

    private static GeneratedRequirement toRequirement(Item item) {
        if (item == null || isBlank(item.title()) || isBlank(item.description())) return null;
        RequirementType type = parse(RequirementType.class, item.type(), null);
        if (type == null) return null;
        Priority priority = parse(Priority.class, item.priority(), Priority.SHOULD);

        List<String> criteria = item.acceptanceCriteria() == null ? List.of()
                : item.acceptanceCriteria().stream()
                        .filter(c -> !isBlank(c))
                        .limit(MAX_CRITERIA)
                        .map(c -> truncate(c, 500))
                        .toList();
        return new GeneratedRequirement(type, RequirementContent.clean(
                truncate(item.category(), 60),
                truncate(item.title(), 200),
                truncate(item.description(), 4_000),
                criteria,
                priority));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    // --- Rendering -------------------------------------------------------------

    static String renderBrief(ProjectBrief brief) {
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(brief.title()).append('\n');
        text.append("Summary: ").append(brief.summary()).append('\n');
        if (!isBlank(brief.problem())) text.append("Problem: ").append(brief.problem()).append('\n');
        appendList(text, "Target users", brief.targetUsers());
        appendList(text, "Goals", brief.goals());
        text.append("Core features:\n");
        for (ProjectBrief.Feature feature : brief.coreFeatures()) {
            text.append("- ").append(feature.name()).append(": ").append(feature.description()).append('\n');
        }
        appendList(text, "Out of scope", brief.outOfScope());
        appendList(text, "Constraints", brief.constraints());
        appendList(text, "Open questions", brief.openQuestions());
        return text.toString().strip();
    }

    private static void appendList(StringBuilder text, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        text.append(heading).append(":\n");
        items.forEach(item -> text.append("- ").append(item).append('\n'));
    }

    static String renderClarifications(List<IntakeResponse.Round> rounds) {
        StringBuilder text = new StringBuilder();
        for (IntakeResponse.Round round : rounds) {
            for (IntakeResponse.Question q : round.questions()) {
                text.append("Q: ").append(q.question()).append('\n');
                text.append("A: ").append(isBlank(q.answer()) ? "(skipped)" : q.answer()).append('\n');
            }
        }
        return text.isEmpty() ? "(none)" : text.toString().strip();
    }

    static String renderExisting(List<Requirement> existing) {
        if (existing.isEmpty()) return "(none)";
        StringBuilder text = new StringBuilder();
        for (Requirement r : existing) {
            text.append(r.code()).append(" [").append(r.getStatus()).append(", ").append(r.getPriority()).append("] ")
                    .append(r.getTitle()).append(": ").append(r.getDescription()).append('\n');
        }
        return text.toString().strip();
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
