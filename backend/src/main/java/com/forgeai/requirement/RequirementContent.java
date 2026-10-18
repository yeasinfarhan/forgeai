package com.forgeai.requirement;

import java.util.List;

/** The editable part of a requirement, already cleaned (trimmed, blank criteria removed). */
record RequirementContent(String category, String title, String description, List<String> acceptanceCriteria, Priority priority) {

    static RequirementContent clean(String category, String title, String description, List<String> criteria, Priority priority) {
        List<String> cleanedCriteria = criteria == null ? List.of()
                : criteria.stream()
                        .filter(c -> c != null && !c.isBlank())
                        // Criteria are stored one per line, so a criterion itself must be a single line.
                        .map(c -> c.replaceAll("\\s*\\R\\s*", " ").strip())
                        .toList();
        return new RequirementContent(
                category == null || category.isBlank() ? null : category.strip(),
                title.strip(),
                description == null ? "" : description.strip(),
                cleanedCriteria,
                priority);
    }
}
