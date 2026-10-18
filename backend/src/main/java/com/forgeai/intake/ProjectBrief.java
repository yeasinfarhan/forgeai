package com.forgeai.intake;

import java.util.List;

/**
 * The outcome of idea intake: a structured description of the software to build. Later phases
 * (requirements, architecture …) start from this.
 */
public record ProjectBrief(
        String title,
        String summary,
        String problem,
        List<String> targetUsers,
        List<String> goals,
        List<Feature> coreFeatures,
        List<String> outOfScope,
        List<String> constraints,
        List<String> openQuestions) {

    public record Feature(String name, String description) {
    }

    /** Plain-text rendering used in AI prompts of later phases. */
    public String toPromptText() {
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(title).append('\n');
        text.append("Summary: ").append(summary).append('\n');
        if (problem != null && !problem.isBlank()) text.append("Problem: ").append(problem).append('\n');
        appendList(text, "Target users", targetUsers);
        appendList(text, "Goals", goals);
        if (coreFeatures != null && !coreFeatures.isEmpty()) {
            text.append("Core features:\n");
            for (Feature feature : coreFeatures) {
                text.append("- ").append(feature.name()).append(": ").append(feature.description()).append('\n');
            }
        }
        appendList(text, "Out of scope", outOfScope);
        appendList(text, "Constraints", constraints);
        appendList(text, "Open questions", openQuestions);
        return text.toString().strip();
    }

    private static void appendList(StringBuilder text, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        text.append(heading).append(":\n");
        items.forEach(item -> text.append("- ").append(item).append('\n'));
    }
}
