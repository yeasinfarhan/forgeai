package com.forgeai.task;

import java.util.List;

/**
 * The list-valued parts of a task, stored as JSON.
 *
 * @param dependsOn    codes of tasks that must be done first (e.g. T-002)
 * @param requirements requirement codes the task implements (e.g. FR-001)
 * @param entities     data-model entities it touches (table names)
 * @param endpoints    API endpoints it implements, as "METHOD /path" relative to the base path
 * @param components   architecture components it belongs to
 */
public record TaskDetails(
        List<String> acceptanceCriteria,
        List<String> dependsOn,
        List<String> requirements,
        List<String> entities,
        List<String> endpoints,
        List<String> components) {

    public TaskDetails {
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        entities = entities == null ? List.of() : List.copyOf(entities);
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        components = components == null ? List.of() : List.copyOf(components);
    }

    TaskDetails withDependsOn(List<String> codes) {
        return new TaskDetails(acceptanceCriteria, codes, requirements, entities, endpoints, components);
    }
}
