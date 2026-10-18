package com.forgeai.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TaskRulesTest {

    @Test
    void estimatesSnapToTheScale() {
        assertThat(TaskAssistant.estimate(null)).isNull();
        assertThat(TaskAssistant.estimate(0)).isNull();
        assertThat(TaskAssistant.estimate(4)).isEqualTo(5);
        assertThat(TaskAssistant.estimate(7)).isEqualTo(8);
        assertThat(TaskAssistant.estimate(40)).isEqualTo(13);
    }

    @Test
    void reachesFollowsDependenciesTransitively() {
        Map<String, List<String>> graph = Map.of("T-003", List.of("T-002"), "T-002", List.of("T-001"), "T-001", List.of());
        assertThat(TaskRules.reaches("T-003", "T-001", graph)).isTrue();
        assertThat(TaskRules.reaches("T-001", "T-003", graph)).isFalse();
    }

    @Test
    void endpointsAreNormalised() {
        assertThat(TaskRules.normaliseEndpoint("get   /invoices/")).isEqualTo("GET /invoices");
        assertThat(TaskRules.normaliseEndpoint("DELETE /invoices/{id}")).isEqualTo("DELETE /invoices/{id}");
    }
}
