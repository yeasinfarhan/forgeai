package com.forgeai.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** Architecture end to end, with the AI provider mocked. Requirements are added by hand and approved. */
class ArchitectureApiIntegrationTest extends IntegrationTest {

    /** Deliberately imperfect model output: duplicates, unknown references, a bad kind, code fences. */
    private static final String GENERATED = """
            {"overview": "A React web app talks to a Spring Boot API backed by PostgreSQL.",
             "style": "Client + REST API + database", "styleRationale": "Small team, one deployable backend.",
             "components": [
               {"name": "Web app", "kind": "CLIENT", "responsibility": "Screens for invoices.", "technology": "React",
                "dependsOn": ["Invoice API", "Web app", "Ghost service"], "requirements": ["fr-001", "FR-999"]},
               {"name": "Invoice API", "kind": "backend", "responsibility": "Business rules and persistence.",
                "technology": "Spring Boot", "dependsOn": ["database"], "requirements": ["FR-001", "NFR-001"]},
               {"name": "Database", "kind": "DATA_STORE", "responsibility": "Stores invoices.", "technology": "PostgreSQL",
                "dependsOn": [], "requirements": []},
               {"name": "web app", "kind": "CLIENT", "responsibility": "Duplicate.", "technology": "Vue",
                "dependsOn": [], "requirements": []}
             ],
             "decisions": [
               {"title": "Use PostgreSQL", "context": "Invoices are relational.", "decision": "PostgreSQL for all data.",
                "alternatives": ["MongoDB: weaker relations", " "], "consequences": "Schema changes need migrations.",
                "requirements": ["FR-001"]},
               {"title": "Incomplete", "context": "x", "decision": "y", "alternatives": [], "consequences": "", "requirements": []}
             ],
             "flows": [
               {"title": "Create an invoice", "description": "User saves an invoice.",
                "diagram": "```mermaid\\nsequenceDiagram\\n  actor User\\n  participant API as Invoice API\\n  User->>API: Save invoice\\n```"},
               {"title": "Empty", "description": "", "diagram": "```mermaid\\n```"}
             ]}
            """;

    private static final String VALID_EDIT = """
            {"content": {"overview": "Edited overview.", "style": "Modular monolith", "styleRationale": "Simple to run.",
              "components": [
                {"name": "Web app", "kind": "CLIENT", "responsibility": "Screens.", "technology": "React",
                 "dependsOn": ["api"], "requirements": ["FR-001", "fr-002"]},
                {"name": "API", "kind": "SERVICE", "responsibility": "Rules.", "technology": null,
                 "dependsOn": [], "requirements": ["NFR-001"]}],
              "decisions": [], "flows": []},
             "version": %d}
            """;

    @MockitoBean
    private AiClient ai;

    private String token;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        projectId = createProject(token, "Invoice tracker");
    }

    @Test
    void generationNeedsApprovedRequirements() throws Exception {
        perform(get("/api/projects/{id}/architecture", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.architecture").value(nullValue()))
                .andExpect(jsonPath("$.requirementsTotal").value(0))
                .andExpect(jsonPath("$.canGenerate").value(false));
        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Add and approve requirements")));

        createRequirements();
        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("0 of 3 are approved")));
        perform(get("/api/projects/{id}/architecture/revisions", projectId)).andExpect(status().isNotFound());
        verify(ai, never()).complete(any());
    }

    @Test
    void generatesAProposalAndRepairsModelOutput() throws Exception {
        approvedRequirements();
        when(ai.complete(any())).thenReturn(reply(GENERATED));

        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsApproved").value(3))
                .andExpect(jsonPath("$.requirements[0].code").value("FR-001"))
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.architecture.status").value("DRAFT"))
                .andExpect(jsonPath("$.architecture.revision").value(1))
                .andExpect(jsonPath("$.architecture.version").value(0))
                .andExpect(jsonPath("$.architecture.model").value("test-model"))
                .andExpect(jsonPath("$.architecture.edited").value(false))
                .andExpect(jsonPath("$.architecture.requirementsChanged").value(false))
                .andExpect(jsonPath("$.architecture.uncoveredRequirements[0]").value("FR-002"))
                .andExpect(jsonPath("$.architecture.uncoveredRequirements.length()").value(1))
                // duplicate "web app" dropped
                .andExpect(jsonPath("$.architecture.content.components.length()").value(3))
                // self and unknown dependencies dropped, unknown code dropped, code upper-cased
                .andExpect(jsonPath("$.architecture.content.components[0].dependsOn.length()").value(1))
                .andExpect(jsonPath("$.architecture.content.components[0].dependsOn[0]").value("Invoice API"))
                .andExpect(jsonPath("$.architecture.content.components[0].requirements.length()").value(1))
                .andExpect(jsonPath("$.architecture.content.components[0].requirements[0]").value("FR-001"))
                // unknown kind falls back to SERVICE; dependency spelled like the component
                .andExpect(jsonPath("$.architecture.content.components[1].kind").value("SERVICE"))
                .andExpect(jsonPath("$.architecture.content.components[1].dependsOn[0]").value("Database"))
                // incomplete decision and empty flow dropped, blank alternative dropped, fences removed
                .andExpect(jsonPath("$.architecture.content.decisions.length()").value(1))
                .andExpect(jsonPath("$.architecture.content.decisions[0].alternatives.length()").value(1))
                .andExpect(jsonPath("$.architecture.content.flows.length()").value(1))
                .andExpect(jsonPath("$.architecture.content.flows[0].diagram", startsWith("sequenceDiagram")))
                .andExpect(jsonPath("$.architecture.content.flows[0].diagram", not(containsString("```"))));

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo("architecture.generate");
        assertThat(request.getValue().jsonSchema()).isEqualTo(ArchitectureSchemas.GENERATED);
        assertThat(request.getValue().userMessage())
                .contains("<project>\nInvoice tracker\n</project>")
                .contains("(no brief")
                .contains("FR-001 [MUST, Invoices] Create an invoice: Users can create an invoice.")
                .contains("  Acceptance: A saved invoice appears in the list")
                .contains("NFR-001 [MUST, Security]");
    }

    @Test
    void editsAreValidatedAndVersioned() throws Exception {
        approvedRequirements();
        when(ai.complete(any())).thenReturn(reply(GENERATED));
        generate();

        // References must point at real components and requirements
        edit(VALID_EDIT.formatted(0).replace("\"dependsOn\": [\"api\"]", "\"dependsOn\": [\"Missing\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("\"Missing\", which is not a component")));
        edit(VALID_EDIT.formatted(0).replace("\"NFR-001\"", "\"NFR-404\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]", containsString("NFR-404")));
        edit(VALID_EDIT.formatted(0).replace("\"Edited overview.\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("content.overview"));

        edit(VALID_EDIT.formatted(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.architecture.edited").value(true))
                .andExpect(jsonPath("$.architecture.revision").value(2))
                .andExpect(jsonPath("$.architecture.version").value(1))
                .andExpect(jsonPath("$.architecture.content.style").value("Modular monolith"))
                .andExpect(jsonPath("$.architecture.content.components[1].requirements[0]").value("NFR-001"))
                .andExpect(jsonPath("$.architecture.content.components[0].requirements[1]").value("FR-002"))
                .andExpect(jsonPath("$.architecture.uncoveredRequirements.length()").value(0));

        // A stale version is rejected
        edit(VALID_EDIT.formatted(0)).andExpect(status().isConflict());
    }

    @Test
    void reviewWorkflowLocksTheApprovedArchitectureAndKeepsHistory() throws Exception {
        approvedRequirements();
        when(ai.complete(any())).thenReturn(reply(GENERATED));
        generate();

        changeStatus("APPROVED", 0).andExpect(status().isConflict()); // review cannot be skipped
        changeStatus("IN_REVIEW", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.architecture.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.canGenerate").value(false));
        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("back to draft")));
        changeStatus("APPROVED", 1).andExpect(status().isOk()).andExpect(jsonPath("$.architecture.status").value("APPROVED"));

        edit(VALID_EDIT.formatted(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Reopen")));

        changeStatus("DRAFT", 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.architecture.status").value("DRAFT"))
                .andExpect(jsonPath("$.architecture.revision").value(4));

        perform(get("/api/projects/{id}/architecture/revisions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].revision").value(4))
                .andExpect(jsonPath("$[0].change").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[2].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$[3].change").value("GENERATED"))
                .andExpect(jsonPath("$[3].model").value("test-model"));
        perform(get("/api/projects/{id}/architecture/revisions/1", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.style").value("Client + REST API + database"));
        perform(get("/api/projects/{id}/architecture/revisions/99", projectId)).andExpect(status().isNotFound());

        // Regenerating in draft replaces the content; the earlier proposal stays in the history
        when(ai.complete(any())).thenReturn(reply(GENERATED.replace("Client + REST API + database", "Modular monolith")));
        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.architecture.revision").value(5))
                .andExpect(jsonPath("$.architecture.content.style").value("Modular monolith"));
        perform(get("/api/projects/{id}/architecture/revisions/1", projectId))
                .andExpect(jsonPath("$.content.style").value("Client + REST API + database"));
    }

    @Test
    void changedRequirementsAreFlaggedAndBlockApproval() throws Exception {
        String[] ids = approvedRequirements();
        when(ai.complete(any())).thenReturn(reply(GENERATED));
        generate();

        // Reopen FR-001 (created v0, in review v1, approved v2)
        perform(post("/api/projects/{p}/requirements/{r}/status", projectId, ids[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DRAFT\", \"version\": 2}"))
                .andExpect(status().isOk());

        perform(get("/api/projects/{id}/architecture", projectId))
                .andExpect(jsonPath("$.architecture.requirementsChanged").value(true))
                .andExpect(jsonPath("$.requirementsApproved").value(2))
                .andExpect(jsonPath("$.canGenerate").value(false));

        changeStatus("IN_REVIEW", 0).andExpect(status().isOk());
        changeStatus("APPROVED", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Approve all requirements")));
    }

    @Test
    void otherUsersCannotSeeTheArchitecture() throws Exception {
        approvedRequirements();
        when(ai.complete(any())).thenReturn(reply(GENERATED));
        generate();

        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/architecture", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{id}/architecture/revisions/1", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private static AiResult reply(String json) {
        return new AiResult(json, "test-model", 100, 50);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private void generate() throws Exception {
        perform(post("/api/projects/{id}/architecture/generate", projectId)).andExpect(status().isOk());
    }

    private ResultActions edit(String body) throws Exception {
        return perform(put("/api/projects/{id}/architecture", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions changeStatus(String target, int version) throws Exception {
        return perform(post("/api/projects/{id}/architecture/status", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\", \"version\": %d}".formatted(target, version)));
    }

    /** FR-001, FR-002 and NFR-001, all approved. Returns their ids in that order. */
    private String[] approvedRequirements() throws Exception {
        String[] ids = createRequirements();
        for (String[] move : new String[][] {{"DRAFT", "IN_REVIEW"}, {"IN_REVIEW", "APPROVED"}}) {
            perform(post("/api/projects/{id}/requirements/bulk-status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"from\": \"%s\", \"to\": \"%s\"}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
        return ids;
    }

    private String[] createRequirements() throws Exception {
        return new String[] {
            createRequirement("FUNCTIONAL", "Invoices", "Create an invoice", "Users can create an invoice.",
                    "A saved invoice appears in the list"),
            createRequirement("FUNCTIONAL", "Invoices", "Export an invoice", "Users can download an invoice as PDF.",
                    "The PDF lists all line items"),
            createRequirement("NON_FUNCTIONAL", "Security", "Protect account data",
                    "Users only see their own invoices.", "Another user's invoice returns not found"),
        };
    }

    private String createRequirement(String type, String category, String title, String description, String criterion)
            throws Exception {
        String response = perform(post("/api/projects/{id}/requirements", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "%s", "category": "%s", "title": "%s", "description": "%s",
                                 "acceptanceCriteria": ["%s"], "priority": "MUST"}
                                """.formatted(type, category, title, description, criterion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
