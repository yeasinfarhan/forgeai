package com.forgeai.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.forgeai.architecture.ArchitectureContent.Component;
import com.forgeai.architecture.ArchitectureContent.Decision;
import com.forgeai.architecture.ArchitectureContent.Flow;

class ArchitectureRulesTest {

    @Test
    void cleanDiagramRemovesCodeFences() {
        assertThat(ArchitectureRules.cleanDiagram("```mermaid\nsequenceDiagram\n  A->>B: Hi\n```"))
                .isEqualTo("sequenceDiagram\n  A->>B: Hi");
        assertThat(ArchitectureRules.cleanDiagram("```\nsequenceDiagram\n```  ")).isEqualTo("sequenceDiagram");
        assertThat(ArchitectureRules.cleanDiagram("  sequenceDiagram\n  A->>B: Hi  ")).isEqualTo("sequenceDiagram\n  A->>B: Hi");
    }

    @Test
    void tidyTrimsAndDeduplicates() {
        ArchitectureContent tidy = ArchitectureRules.tidy(content(
                new Component(" API ", ComponentKind.SERVICE, " Rules ", "  ", List.of("DB", "db", " "), List.of("fr-001", "FR-001"))));

        Component api = tidy.components().getFirst();
        assertThat(api.name()).isEqualTo("API");
        assertThat(api.technology()).isNull();
        assertThat(api.dependsOn()).containsExactly("DB");
        assertThat(api.requirements()).containsExactly("FR-001");
    }

    @Test
    void problemsListBrokenReferences() {
        ArchitectureContent content = content(
                new Component("API", ComponentKind.SERVICE, "Rules", null, List.of("API", "Queue"), List.of("FR-001", "FR-009")),
                new Component("api", ComponentKind.SERVICE, "Duplicate", null, List.of(), List.of()));

        assertThat(ArchitectureRules.problems(content, Set.of("FR-001")))
                .containsExactly(
                        "Two components are called \"api\". Component names must be unique.",
                        "\"API\" cannot depend on itself.",
                        "\"API\" depends on \"Queue\", which is not a component.",
                        "\"API\" refers to FR-009, which is not a requirement of this project.");
    }

    @Test
    void coveredRequirementsComeFromComponentsAndDecisions() {
        ArchitectureContent content = new ArchitectureContent("Overview", "Monolith", "Simple",
                List.of(new Component("API", ComponentKind.SERVICE, "Rules", null, List.of(), List.of("FR-001"))),
                List.of(new Decision("Use PostgreSQL", "Context", "Decision", List.of(), "Consequences", List.of("NFR-001"))),
                List.of(new Flow("Sign in", null, "sequenceDiagram")));

        assertThat(ArchitectureRules.coveredRequirements(content)).containsExactly("FR-001", "NFR-001");
    }

    private static ArchitectureContent content(Component... components) {
        return new ArchitectureContent("Overview", "Monolith", "Simple", List.of(components), List.of(), List.of());
    }
}
