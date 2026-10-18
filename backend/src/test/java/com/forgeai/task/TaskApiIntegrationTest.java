package com.forgeai.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

/** The development backlog end to end, with the AI provider mocked and every earlier stage approved. */
class TaskApiIntegrationTest extends IntegrationTest {

    private static final String ARCHITECTURE = """
            {"overview": "A web app and a REST API on PostgreSQL.", "style": "Client + REST API + database",
             "styleRationale": "Small team.",
             "components": [
               {"name": "API", "kind": "SERVICE", "responsibility": "Business rules.", "technology": "Spring Boot",
                "dependsOn": [], "requirements": ["FR-001"]},
               {"name": "Web app", "kind": "CLIENT", "responsibility": "Screens.", "technology": "React",
                "dependsOn": ["API"], "requirements": ["FR-001"]}],
             "decisions": [], "flows": []}
            """;

    private static final String DATABASE = """
            {"summary": "Customers receive invoices.", "entities": [
              {"name": "customers", "description": "", "indexes": [], "requirements": [], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": ""}]},
              {"name": "invoices", "description": "", "indexes": [], "requirements": ["FR-001"], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": ""},
                {"name": "customer_id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": false, "unique": false, "defaultValue": null,
                 "references": {"entity": "customers", "column": "id", "onDelete": "RESTRICT"}, "allowedValues": [], "description": ""}]}
            ]}
            """;

    private static final String API = """
            {"summary": "Invoices API.", "basePath": "/api", "authentication": "Bearer token.", "errorFormat": "Problem details.",
             "errors": [{"status": 404, "code": "not_found", "description": "Missing."}],
             "schemas": [{"name": "InvoiceResponse", "description": "", "entity": "invoices", "fields": [
               {"name": "id", "type": "UUID", "ref": null, "array": false, "required": true, "description": ""}]}],
             "endpoints": [
               {"method": "GET", "path": "/invoices", "summary": "List invoices", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": null, "responseSchema": "InvoiceResponse", "responseList": true,
                "successStatus": 200, "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": ["FR-001"]},
               {"method": "POST", "path": "/invoices", "summary": "Create an invoice", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": "InvoiceResponse", "responseSchema": "InvoiceResponse", "responseList": false,
                "successStatus": 201, "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": ["FR-002"]}]}
            """;

    /** Deliberately imperfect: out of order, a cycle, unknown references, estimates off the scale. */
    private static final String TASKS = """
            {"tasks": [
              {"ref": "N1", "title": "Set up the project", "description": "Create the skeleton.", "type": "SETUP", "estimate": 2,
               "acceptanceCriteria": ["It builds", " "], "dependsOn": [], "requirements": [], "entities": [], "endpoints": [],
               "components": ["API"]},
              {"ref": "N3", "title": "Implement the invoice list endpoint", "description": "Return invoices.", "type": "BACKEND",
               "estimate": 4, "acceptanceCriteria": ["Returns the list"], "dependsOn": ["N2"], "requirements": ["FR-001", "FR-999"],
               "entities": ["invoices"], "endpoints": ["get /invoices/"], "components": ["Ghost"]},
              {"ref": "N2", "title": "Create the database schema", "description": "Tables and migrations.", "type": "DATABASE",
               "estimate": 20, "acceptanceCriteria": [], "dependsOn": ["N1", "N3", "T-999"], "requirements": [],
               "entities": ["customers", "invoices", "ghosts"], "endpoints": [], "components": []},
              {"ref": "N4", "title": "Build the invoice page", "description": "Show invoices.", "type": "WEBSITE", "estimate": 0,
               "acceptanceCriteria": [], "dependsOn": ["N3", "N4"], "requirements": ["FR-001"], "entities": [], "endpoints": [],
               "components": ["Web app"]},
              {"ref": "N5", "title": " ", "description": "No title.", "type": "BACKEND", "estimate": 1, "acceptanceCriteria": [],
               "dependsOn": [], "requirements": [], "entities": [], "endpoints": [], "components": []}
            ]}
            """;

    private static final String REGENERATED = """
            {"tasks": [
              {"ref": "A", "title": "Implement invoice creation", "description": "POST /invoices.", "type": "BACKEND", "estimate": 3,
               "acceptanceCriteria": ["Creates an invoice"], "dependsOn": ["T-001", "T-002"], "requirements": ["FR-002"],
               "entities": ["invoices"], "endpoints": ["POST /invoices"], "components": ["API"]}
            ]}
            """;

    @MockitoBean
    private AiClient ai;

    private String token;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        projectId = createProject(token, "Invoice Tracker");
    }

    @Test
    void generationNeedsTheApprovedApiDesign() throws Exception {
        approvedRequirements();
        perform(get("/api/projects/{id}/tasks", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.blocker").value("Approve the architecture first."))
                .andExpect(jsonPath("$.findings.length()").value(0));
        perform(post("/api/projects/{id}/tasks/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Approve the architecture first."));
    }

    @Test
    void generatesAnOrderedRepairedBacklog() throws Exception {
        approvedDesign();
        when(ai.complete(any())).thenReturn(reply(TASKS));

        perform(post("/api/projects/{id}/tasks/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(4)) // untitled task dropped
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.inputsChanged").value(false))
                // ordered so that every task comes after its dependencies, then numbered
                .andExpect(jsonPath("$.tasks[0].code").value("T-001"))
                .andExpect(jsonPath("$.tasks[0].title").value("Set up the project"))
                .andExpect(jsonPath("$.tasks[0].acceptanceCriteria.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].components[0]").value("API"))
                .andExpect(jsonPath("$.tasks[1].title").value("Create the database schema"))
                .andExpect(jsonPath("$.tasks[1].dependsOn.length()").value(1)) // cycle and unknown code dropped
                .andExpect(jsonPath("$.tasks[1].dependsOn[0]").value("T-001"))
                .andExpect(jsonPath("$.tasks[1].estimate").value(13))
                .andExpect(jsonPath("$.tasks[1].entities.length()").value(2))
                .andExpect(jsonPath("$.tasks[2].title").value("Implement the invoice list endpoint"))
                .andExpect(jsonPath("$.tasks[2].dependsOn[0]").value("T-002"))
                .andExpect(jsonPath("$.tasks[2].estimate").value(5))
                .andExpect(jsonPath("$.tasks[2].requirements.length()").value(1))
                .andExpect(jsonPath("$.tasks[2].endpoints[0]").value("GET /invoices"))
                .andExpect(jsonPath("$.tasks[2].components.length()").value(0))
                .andExpect(jsonPath("$.tasks[2].blockedBy[0]").value("T-002"))
                .andExpect(jsonPath("$.tasks[3].type").value("BACKEND"))
                .andExpect(jsonPath("$.tasks[3].estimate").doesNotExist())
                .andExpect(jsonPath("$.tasks[3].dependsOn[0]").value("T-003"))
                .andExpect(jsonPath("$.tasks[3].dependsOn.length()").value(1))
                .andExpect(jsonPath("$.counts.total").value(4))
                .andExpect(jsonPath("$.counts.points").value(20))
                .andExpect(jsonPath("$.refs.endpoints.length()").value(2))
                .andExpect(jsonPath("$.findings[*].message", hasItem("No task implements POST /invoices.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem("No task implements FR-002 (Create an invoice).")))
                .andExpect(jsonPath("$.findings[*].message", not(hasItem(containsString("GET /invoices")))));

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(4)).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo("tasks.generate");
        assertThat(request.getValue().userMessage())
                .contains("- GET /invoices: List invoices (entity invoices) [FR-001]")
                .contains("- API [SERVICE, Spring Boot]")
                .contains("<existing_tasks>\n(none)");
    }

    @Test
    void theBoardFollowsDependencies() throws Exception {
        approvedDesign();
        when(ai.complete(any())).thenReturn(reply(TASKS));
        String overview = generate();

        moveTo(overview, "T-003", "IN_PROGRESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("T-003 is waiting for T-002 to be done."));
        overview = ok(moveTo(overview, "T-001", "DONE"));
        overview = ok(moveTo(overview, "T-002", "IN_PROGRESS"));
        overview = ok(moveTo(overview, "T-002", "DONE"));
        perform(post("/api/projects/{p}/tasks/{t}/status", projectId, id(overview, "T-003"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\", \"version\": %d}".formatted(version(overview, "T-003"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.done").value(2))
                .andExpect(jsonPath("$.counts.inProgress").value(1))
                .andExpect(jsonPath("$.counts.pointsDone").value(15))
                .andExpect(jsonPath("$.tasks[2].blockedBy.length()").value(0));

        perform(get("/api/projects/{p}/tasks/{t}/events", projectId, id(overview, "T-002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].toStatus").value("DONE"))
                .andExpect(jsonPath("$[0].fromStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[2].type").value("CREATED"));

        perform(delete("/api/projects/{p}/tasks/{t}", projectId, id(overview, "T-002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("T-003 depends on T-002. Remove the dependency first."));
    }

    @Test
    void editsAreValidatedAndTheOrderCanChange() throws Exception {
        approvedDesign();
        when(ai.complete(any())).thenReturn(reply(TASKS));
        String overview = generate();

        String t1 = id(overview, "T-001");
        int v1 = version(overview, "T-001");
        save(put("/api/projects/{p}/tasks/{t}", projectId, t1), task("[\"T-004\"]", "[]", 2, v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value(
                        "T-004 already depends on T-001 (directly or through other tasks), so T-001 cannot depend on T-004."));
        save(put("/api/projects/{p}/tasks/{t}", projectId, t1), task("[]", "[\"DELETE /nope\"]", 2, v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("DELETE /nope is not an endpoint of the API design."));
        save(put("/api/projects/{p}/tasks/{t}", projectId, t1), task("[]", "[]", 4, v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("The estimate must be one of 1, 2, 3, 5, 8 or 13 points."));
        overview = ok(save(put("/api/projects/{p}/tasks/{t}", projectId, t1), task("[]", "[\"post /invoices\"]", 3, v1)));
        assertThat(JsonPath.<List<Boolean>>read(overview, "$.tasks[?(@.code == 'T-001')].edited")).containsExactly(true);
        assertThat(JsonPath.<List<String>>read(overview, "$.findings[*].message")).doesNotContain("No task implements POST /invoices.");
        save(put("/api/projects/{p}/tasks/{t}", projectId, t1), task("[]", "[]", 3, v1)).andExpect(status().isConflict()); // stale

        // A hand-written task is appended; moving it above its dependency is allowed but reported
        overview = ok(save(post("/api/projects/{id}/tasks", projectId), task("[\"T-003\"]", "[]", 1, null)).andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(overview, "$.tasks[4].code")).isEqualTo("T-005");
        assertThat(JsonPath.<String>read(overview, "$.tasks[4].source")).isEqualTo("USER");
        perform(post("/api/projects/{p}/tasks/{t}/move", projectId, id(overview, "T-005"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\": 0, \"version\": %d}".formatted(version(overview, "T-005"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].code").value("T-005"))
                .andExpect(jsonPath("$.tasks[1].code").value("T-001"))
                .andExpect(jsonPath("$.tasks[4].position").value(4))
                .andExpect(jsonPath("$.findings[*].message", hasItem("T-005 comes before T-003, which it depends on.")));

        perform(delete("/api/projects/{p}/tasks/{t}", projectId, id(overview, "T-005")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(4))
                .andExpect(jsonPath("$.tasks[0].code").value("T-001"))
                .andExpect(jsonPath("$.tasks[0].position").value(0));
    }

    @Test
    void regenerationKeepsStartedAndEditedTasks() throws Exception {
        approvedDesign();
        when(ai.complete(any())).thenReturn(reply(TASKS));
        String overview = generate();
        overview = ok(moveTo(overview, "T-001", "DONE"));
        // Edit T-004 (keeps its dependency on T-003, which regeneration will replace)
        overview = ok(save(put("/api/projects/{p}/tasks/{t}", projectId, id(overview, "T-004")),
                task("[\"T-003\"]", "[]", 2, version(overview, "T-004"))));

        when(ai.complete(any())).thenReturn(reply(REGENERATED));
        perform(post("/api/projects/{id}/tasks/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(3))
                .andExpect(jsonPath("$.tasks[0].code").value("T-001"))
                .andExpect(jsonPath("$.tasks[0].status").value("DONE"))
                .andExpect(jsonPath("$.tasks[1].code").value("T-004"))
                .andExpect(jsonPath("$.tasks[1].dependsOn.length()").value(0)) // T-003 was replaced
                .andExpect(jsonPath("$.tasks[2].code").value("T-005")) // codes are never reused
                .andExpect(jsonPath("$.tasks[2].dependsOn.length()").value(1)) // T-002 is gone
                .andExpect(jsonPath("$.tasks[2].dependsOn[0]").value("T-001"))
                .andExpect(jsonPath("$.tasks[2].position").value(2));

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(5)).complete(request.capture());
        assertThat(request.getValue().userMessage())
                .contains("T-001 [DONE, SETUP] Set up the project")
                .contains("T-004 [TODO, FRONTEND] Hand-written task (after T-003)");
    }

    @Test
    void otherUsersCannotSeeTheBacklog() throws Exception {
        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/tasks", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private static AiResult reply(String json) {
        return new AiResult(json, "test-model", 100, 50);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions save(MockHttpServletRequestBuilder request, String body) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String task(String dependsOn, String endpoints, int estimate, Integer version) {
        return """
                {"title": "Hand-written task", "description": "Done by hand.", "type": "FRONTEND", "estimate": %d,
                 "acceptanceCriteria": ["Works"], "dependsOn": %s, "requirements": ["FR-001"], "entities": [],
                 "endpoints": %s, "components": [], "version": %s}
                """.formatted(estimate, dependsOn, endpoints, version);
    }

    private String generate() throws Exception {
        return ok(perform(post("/api/projects/{id}/tasks/generate", projectId)));
    }

    private static String ok(ResultActions actions) throws Exception {
        int status = actions.andReturn().getResponse().getStatus();
        assertThat(status).as(actions.andReturn().getResponse().getContentAsString()).isBetween(200, 201);
        return actions.andReturn().getResponse().getContentAsString();
    }

    private ResultActions moveTo(String overview, String code, String target) throws Exception {
        return perform(post("/api/projects/{p}/tasks/{t}/status", projectId, id(overview, code))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\", \"version\": %d}".formatted(target, version(overview, code))));
    }

    private static String id(String overview, String code) {
        return JsonPath.<List<String>>read(overview, "$.tasks[?(@.code == '" + code + "')].id").getFirst();
    }

    private static int version(String overview, String code) {
        return JsonPath.<List<Integer>>read(overview, "$.tasks[?(@.code == '" + code + "')].version").getFirst();
    }

    /** FR-001, FR-002, FR-003 and NFR-001, all approved. */
    private void approvedRequirements() throws Exception {
        for (String[] r : new String[][] {
                {"FUNCTIONAL", "List invoices"}, {"FUNCTIONAL", "Create an invoice"}, {"FUNCTIONAL", "Send reminders"},
                {"NON_FUNCTIONAL", "Protect account data"}}) {
            save(post("/api/projects/{id}/requirements", projectId), """
                    {"type": "%s", "category": "General", "title": "%s", "description": "%s.",
                     "acceptanceCriteria": ["It works"], "priority": "MUST"}
                    """.formatted(r[0], r[1], r[1])).andExpect(status().isCreated());
        }
        for (String[] move : new String[][] {{"DRAFT", "IN_REVIEW"}, {"IN_REVIEW", "APPROVED"}}) {
            save(post("/api/projects/{id}/requirements/bulk-status", projectId),
                    "{\"from\": \"%s\", \"to\": \"%s\"}".formatted(move[0], move[1])).andExpect(status().isOk());
        }
    }

    /** Requirements, architecture, database design and API design, all approved. */
    private void approvedDesign() throws Exception {
        approvedRequirements();
        approveStage("architecture", ARCHITECTURE);
        approveStage("database-design", DATABASE);
        approveStage("api-design", API);
    }

    private void approveStage(String stage, String generated) throws Exception {
        when(ai.complete(any())).thenReturn(reply(generated));
        perform(post("/api/projects/{id}/" + stage + "/generate", projectId)).andExpect(status().isOk());
        for (String[] move : new String[][] {{"IN_REVIEW", "0"}, {"APPROVED", "1"}}) {
            save(post("/api/projects/{id}/" + stage + "/status", projectId),
                    "{\"status\": \"%s\", \"version\": %s}".formatted(move[0], move[1])).andExpect(status().isOk());
        }
    }
}
