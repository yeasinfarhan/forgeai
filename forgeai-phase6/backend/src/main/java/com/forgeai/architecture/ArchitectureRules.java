package com.forgeai.architecture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.forgeai.architecture.ArchitectureContent.Component;
import com.forgeai.architecture.ArchitectureContent.Decision;
import com.forgeai.architecture.ArchitectureContent.Flow;

/**
 * Rules that span several parts of an {@link ArchitectureContent}: unique component names,
 * dependencies that point at real components, requirement codes that exist. User edits that break
 * them are rejected with a clear message; AI output is repaired instead (see {@link ArchitectureAssistant}).
 */
final class ArchitectureRules {

    private static final Pattern MERMAID_FENCE = Pattern.compile("^```\\s*(mermaid)?\\s*\\n|\\n?```\\s*$");

    private ArchitectureRules() {
    }

    /** Trims text, removes blank and duplicate list entries and normalises codes and diagram fences. */
    static ArchitectureContent tidy(ArchitectureContent content) {
        return new ArchitectureContent(
                content.overview().strip(),
                content.style().strip(),
                content.styleRationale().strip(),
                content.components().stream().map(c -> new Component(
                        c.name().strip(),
                        c.kind(),
                        c.responsibility().strip(),
                        blankToNull(c.technology()),
                        distinct(c.dependsOn(), false),
                        distinct(c.requirements(), true))).toList(),
                content.decisions().stream().map(d -> new Decision(
                        d.title().strip(),
                        d.context().strip(),
                        d.decision().strip(),
                        distinct(d.alternatives(), false),
                        d.consequences().strip(),
                        distinct(d.requirements(), true))).toList(),
                content.flows().stream().map(f -> new Flow(
                        f.title().strip(),
                        blankToNull(f.description()),
                        cleanDiagram(f.diagram()))).toList());
    }

    /** Problems a person has to fix before the content can be saved; empty when it is consistent. */
    static List<String> problems(ArchitectureContent content, Set<String> requirementCodes) {
        List<String> problems = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        for (Component component : content.components()) {
            String previous = names.putIfAbsent(key(component.name()), component.name());
            if (previous != null) {
                problems.add("Two components are called \"" + component.name() + "\". Component names must be unique.");
            }
        }
        for (Component component : content.components()) {
            for (String dependency : component.dependsOn()) {
                if (key(dependency).equals(key(component.name()))) {
                    problems.add("\"" + component.name() + "\" cannot depend on itself.");
                } else if (!names.containsKey(key(dependency))) {
                    problems.add("\"" + component.name() + "\" depends on \"" + dependency + "\", which is not a component.");
                }
            }
            unknownCodes(component.requirements(), requirementCodes).forEach(code ->
                    problems.add("\"" + component.name() + "\" refers to " + code + ", which is not a requirement of this project."));
        }
        for (Decision decision : content.decisions()) {
            unknownCodes(decision.requirements(), requirementCodes).forEach(code ->
                    problems.add("Decision \"" + decision.title() + "\" refers to " + code + ", which is not a requirement of this project."));
        }
        return problems;
    }

    /** Requirement codes referenced anywhere in the content (upper-case). */
    static Set<String> coveredRequirements(ArchitectureContent content) {
        Set<String> covered = new TreeSet<>();
        content.components().forEach(c -> c.requirements().forEach(code -> covered.add(code.toUpperCase(Locale.ROOT))));
        content.decisions().forEach(d -> d.requirements().forEach(code -> covered.add(code.toUpperCase(Locale.ROOT))));
        return covered;
    }

    /** Removes Markdown code fences a model sometimes puts around Mermaid source. */
    static String cleanDiagram(String diagram) {
        return MERMAID_FENCE.matcher(diagram.strip()).replaceAll("").strip();
    }

    static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static List<String> unknownCodes(List<String> codes, Set<String> known) {
        return codes.stream().filter(code -> !known.contains(code.toUpperCase(Locale.ROOT))).toList();
    }

    private static List<String> distinct(List<String> values, boolean upperCase) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> keys = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String cleaned = upperCase ? value.strip().toUpperCase(Locale.ROOT) : value.strip();
            if (keys.add(cleaned.toLowerCase(Locale.ROOT))) seen.add(cleaned);
        }
        return List.copyOf(seen);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
