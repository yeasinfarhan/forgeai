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
}
