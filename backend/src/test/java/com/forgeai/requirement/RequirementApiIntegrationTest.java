package com.forgeai.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

/** Requirements end to end, with the AI provider mocked. */
class RequirementApiIntegrationTest extends IntegrationTest {

    private static final String BRIEF_TURN = """
            {"decision": "write_brief", "questions": [], "brief": {
              "title": "Invoice Tracker", "summary": "Freelancers send invoices and track payments.",
              "problem": "Unpaid invoices get forgotten.", "targetUsers": ["Freelancers"], "goals": ["Get paid on time"],
              "coreFeatures": [{"name": "Invoices", "description": "Create and send invoices."}],
              "outOfScope": ["Accounting"], "constraints": [], "openQuestions": []}}
            """;

    private static final String GENERATED = """
            {"requirements": [
              {"type": "FUNCTIONAL", "category": "Invoices", "title": "Create an invoice",
               "description": "Users can create an invoice for a client.",
               "acceptanceCriteria": ["Given a client, when I add line items, then a total is shown", "  "],
               "priority": "MUST"},
              {"type": "FUNCTIONAL", "category": "Invoices", "title": "Export invoice as PDF",
               "description": "Users can download an invoice as a PDF.",
               "acceptanceCriteria": ["The PDF contains all line items"], "priority": "SHOULD"},
              {"type": "NON_FUNCTIONAL", "category": "Security", "title": "Protect account data",
               "description": "The system shall only show users their own invoices.",
               "acceptanceCriteria": ["Another user's invoice returns not found"], "priority": "MUST"},
              {"type": "SOMETHING_ELSE", "category": "x", "title": "Ignored", "description": "Bad type",
               "acceptanceCriteria": [], "priority": "MUST"}
            ]}
            """;

    private static final String REGENERATED = """
            {"requirements": [
              {"type": "FUNCTIONAL", "category": "Clients", "title": "Manage clients",
               "description": "Users can add and edit clients.", "acceptanceCriteria": ["A new client appears in the list"],
               "priority": "MUST"}
            ]}
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

    private static AiResult reply(String json) {
        return new AiResult(json, "test-model", 100, 50);
    }

    private void writeBrief() throws Exception {
        perform(post("/api/projects/{id}/intake", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\": \"Invoices for freelancers\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BRIEF_READY"));
    }

    @Test
    void generationNeedsABrief() throws Exception {
        perform(get("/api/projects/{id}/requirements", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefReady").value(false))
                .andExpect(jsonPath("$.counts.total").value(0))
                .andExpect(jsonPath("$.allApproved").value(false));

        perform(post("/api/projects/{id}/requirements/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("brief")));
    }

    @Test
    void generatesNumberedDraftsFromTheBrief() throws Exception {
        when(ai.complete(any())).thenReturn(reply(BRIEF_TURN), reply(GENERATED));
        writeBrief();

        perform(post("/api/projects/{id}/requirements/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefReady").value(true))
                .andExpect(jsonPath("$.counts.draft").value(3))
                .andExpect(jsonPath("$.requirements.length()").value(3)) // invalid type dropped
                .andExpect(jsonPath("$.requirements[0].code").value("FR-001"))
                .andExpect(jsonPath("$.requirements[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.requirements[0].source").value("AI"))
                .andExpect(jsonPath("$.requirements[0].revision").value(1))
                .andExpect(jsonPath("$.requirements[0].acceptanceCriteria.length()").value(1)) // blank dropped
                .andExpect(jsonPath("$.requirements[1].code").value("FR-002"))
                .andExpect(jsonPath("$.requirements[2].code").value("NFR-001"))
                .andExpect(jsonPath("$.requirements[2].category").value("Security"));

        ArgumentCaptor<AiRequest> requests = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(2)).complete(requests.capture());
        AiRequest request = requests.getValue();
        assertThat(request.purpose()).isEqualTo("requirements.generate");
        assertThat(request.jsonSchema()).isEqualTo(RequirementSchemas.GENERATED);
        assertThat(request.userMessage())
                .contains("Title: Invoice Tracker")
                .contains("- Invoices: Create and send invoices.")
                .contains("<existing_requirements>\n(none)");
    }

    @Test
    void reviewWorkflowAndHistory() throws Exception {
        when(ai.complete(any())).thenReturn(reply(BRIEF_TURN), reply(GENERATED));
        writeBrief();
        String overview = generate();
        String id = JsonPath.read(overview, "$.requirements[0].id");

        // Edit a draft: new revision, marked as edited
        perform(put("/api/projects/{p}/requirements/{r}", projectId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category": "Invoices", "title": "Create and save an invoice",
                                 "description": "Users can create and save an invoice.",
                                 "acceptanceCriteria": ["Saved invoices appear in the list"], "priority": "MUST", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Create and save an invoice"))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.revision").value(2));

        // Stale version is rejected
        perform(put("/api/projects/{p}/requirements/{r}", projectId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Stale", "description": "Stale edit.", "acceptanceCriteria": [],
                                 "priority": "MUST", "version": 0}
                                """))
                .andExpect(status().isConflict());

        // Skipping review is not allowed; draft -> in review -> approved is
        changeStatus(id, "APPROVED", 1).andExpect(status().isConflict());
        changeStatus(id, "IN_REVIEW", 1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        changeStatus(id, "APPROVED", 2).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        // Approved requirements are locked
        perform(put("/api/projects/{p}/requirements/{r}", projectId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Locked", "description": "Should fail.", "acceptanceCriteria": [],
                                 "priority": "MUST", "version": 3}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Reopen")));
        perform(delete("/api/projects/{p}/requirements/{r}", projectId, id)).andExpect(status().isConflict());

        // Full history, newest first
        perform(get("/api/projects/{p}/requirements/{r}/history", projectId, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].revision").value(4))
                .andExpect(jsonPath("$[0].change").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[2].change").value("EDITED"))
                .andExpect(jsonPath("$[3].change").value("CREATED"))
                .andExpect(jsonPath("$[3].title").value("Create an invoice"));
    }

    @Test
    void bulkActionsMoveWholeLists() throws Exception {
        when(ai.complete(any())).thenReturn(reply(BRIEF_TURN), reply(GENERATED));
        writeBrief();
        generate();

        bulk("DRAFT", "IN_REVIEW").andExpect(status().isOk()).andExpect(jsonPath("$.counts.inReview").value(3));
        bulk("IN_REVIEW", "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.approved").value(3))
                .andExpect(jsonPath("$.allApproved").value(true));
        bulk("DRAFT", "APPROVED").andExpect(status().isConflict());
    }

    @Test
    void regenerationKeepsReviewedAndEditedWorkAndReplacesUntouchedDrafts() throws Exception {
        when(ai.complete(any())).thenReturn(reply(BRIEF_TURN), reply(GENERATED), reply(REGENERATED));
        writeBrief();
        String overview = generate();
        String first = JsonPath.read(overview, "$.requirements[0].id");   // FR-001 -> will be approved
        String nfr = JsonPath.read(overview, "$.requirements[2].id");     // NFR-001 -> untouched draft

        changeStatus(first, "IN_REVIEW", 0).andExpect(status().isOk());
        changeStatus(first, "APPROVED", 1).andExpect(status().isOk());
        String manual = createManual();                                    // FR-003, user-written

        String regenerated = generate();
        List<String> codes = JsonPath.read(regenerated, "$.requirements[*].code");
        List<String> titles = JsonPath.read(regenerated, "$.requirements[*].title");
        List<String> ids = JsonPath.read(regenerated, "$.requirements[*].id");

        assertThat(ids).contains(first, manual).doesNotContain(nfr);
        assertThat(titles).contains("Create an invoice", "Write audit log", "Manage clients")
                .doesNotContain("Export invoice as PDF", "Protect account data");
        assertThat(codes).containsExactly("FR-001", "FR-003", "FR-004");

        ArgumentCaptor<AiRequest> requests = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(3)).complete(requests.capture());
        assertThat(requests.getValue().userMessage())
                .contains("FR-001 [APPROVED, MUST] Create an invoice")
                .contains("FR-003 [DRAFT, COULD] Write audit log");
    }

    @Test
    void manualRequirementsAndDeletion() throws Exception {
        String id = createManual();

        perform(get("/api/projects/{id}/requirements", projectId))
                .andExpect(jsonPath("$.requirements[0].code").value("FR-001"))
                .andExpect(jsonPath("$.requirements[0].source").value("USER"));

        perform(delete("/api/projects/{p}/requirements/{r}", projectId, id)).andExpect(status().isNoContent());
        perform(get("/api/projects/{id}/requirements", projectId)).andExpect(jsonPath("$.counts.total").value(0));
    }

    @Test
    void validationAndOwnership() throws Exception {
        perform(post("/api/projects/{id}/requirements", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"FUNCTIONAL\", \"title\": \"\", \"description\": \"\", \"acceptanceCriteria\": [], \"priority\": \"MUST\"}"))
                .andExpect(status().isBadRequest());

        String id = createManual();
        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/requirements", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/{p}/requirements/{r}", projectId, id).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String generate() throws Exception {
        return perform(post("/api/projects/{id}/requirements/generate", projectId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions changeStatus(String requirementId, String target, int version) throws Exception {
        return perform(post("/api/projects/{p}/requirements/{r}/status", projectId, requirementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\", \"version\": %d}".formatted(target, version)));
    }

    private ResultActions bulk(String from, String to) throws Exception {
        return perform(post("/api/projects/{id}/requirements/bulk-status", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\": \"%s\", \"to\": \"%s\"}".formatted(from, to)));
    }

    private String createManual() throws Exception {
        String response = perform(post("/api/projects/{id}/requirements", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FUNCTIONAL", "category": "Audit", "title": "Write audit log",
                                 "description": "The system shall record who changed an invoice.",
                                 "acceptanceCriteria": ["Each change shows user and time"], "priority": "COULD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
