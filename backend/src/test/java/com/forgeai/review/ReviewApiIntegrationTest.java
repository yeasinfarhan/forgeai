package com.forgeai.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.support.DesignFixtures;
import com.forgeai.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** Code review end to end, with the AI provider mocked, the design approved and some code accepted. */
class ReviewApiIntegrationTest extends IntegrationTest {

    private static final String TASKS = """
            {"tasks": [
              {"ref": "N1", "title": "Set up the backend", "description": "Skeleton.", "type": "SETUP", "estimate": 2,
               "acceptanceCriteria": ["It builds"], "dependsOn": [], "requirements": [], "entities": [], "endpoints": [],
               "components": ["API"]},
              {"ref": "N2", "title": "Implement the invoice list", "description": "GET /invoices.", "type": "BACKEND",
               "estimate": 3, "acceptanceCriteria": ["Returns invoices"], "dependsOn": ["N1"], "requirements": ["FR-001"],
               "entities": ["invoices"], "endpoints": ["GET /invoices"], "components": ["API"]}
            ]}
            """;

    private static final String APP = "backend/src/main/java/App.java";

    /** Deliberately imperfect: a line past the end, an unknown file and requirement, a duplicate, a blank title. */
    private static final String REVIEW = """
            {"summary": "Small but sound skeleton.", "findings": [
              {"severity": "LOW", "category": "MAINTAINABILITY", "path": "backend/src/main/java/App.java", "line": 2,
               "title": "Name the field clearly", "detail": "x says nothing.", "suggestion": "Rename it.", "requirement": null,
               "taskType": "BACKEND"},
              {"severity": "HIGH", "category": "SECURITY", "path": "./backend/src/main/java/App.java", "line": 99,
               "title": "Check who may list invoices", "detail": "Anyone can list them.", "suggestion": "Scope by owner.",
               "requirement": "fr-001", "taskType": "BACKEND"},
              {"severity": "nope", "category": "WEIRD", "path": "missing/File.java", "line": 4,
               "title": "Add tests for the skeleton", "detail": "Nothing is tested.", "suggestion": "",
               "requirement": "FR-999", "taskType": "SOMETHING"},
              {"severity": "HIGH", "category": "SECURITY", "path": "backend/src/main/java/App.java", "line": 1,
               "title": "Check who may list invoices", "detail": "Duplicate.", "suggestion": "Duplicate.",
               "requirement": null, "taskType": "BACKEND"},
              {"severity": "HIGH", "category": "SECURITY", "path": null, "line": null,
               "title": " ", "detail": "No title.", "suggestion": "None.", "requirement": null, "taskType": "BACKEND"}
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
    void reviewNeedsAcceptedCode() throws Exception {
        approvedDesign();
        perform(get("/api/projects/{id}/reviews", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.blocker", containsString("There is no accepted code to review yet.")))
                .andExpect(jsonPath("$.reviews.length()").value(0))
                .andExpect(jsonPath("$.latest").doesNotExist());
        perform(post("/api/projects/{id}/reviews/generate", projectId)).andExpect(status().isConflict());
    }

    @Test
    void findingsAreRepairedAndBecomeTasksOrAreDismissed() throws Exception {
        approvedDesign();
        String t1 = acceptedCode();

        when(ai.complete(any())).thenReturn(reply(REVIEW));
        String review = ok(perform(post("/api/projects/{id}/reviews/generate", projectId)));
        assertThat(JsonPath.<String>read(review, "$.summary")).isEqualTo("Small but sound skeleton.");
        assertThat(JsonPath.<Integer>read(review, "$.filesReviewed")).isEqualTo(2);
        assertThat(JsonPath.<Boolean>read(review, "$.outdated")).isFalse();
        assertThat(JsonPath.<List<String>>read(review, "$.findings[*].title"))
                .containsExactly("Check who may list invoices", "Add tests for the skeleton", "Name the field clearly");
        assertThat(JsonPath.<List<String>>read(review, "$.findings[*].severity")).containsExactly("HIGH", "MEDIUM", "LOW");
        // HIGH: path normalised, line past the end dropped, requirement upper-cased
        assertThat(JsonPath.<String>read(review, "$.findings[0].path")).isEqualTo(APP);
        assertThat(JsonPath.<Object>read(review, "$.findings[0].line")).isNull();
        assertThat(JsonPath.<String>read(review, "$.findings[0].requirementCode")).isEqualTo("FR-001");
        // MEDIUM: unknown file, requirement, category and task type replaced
        assertThat(JsonPath.<Object>read(review, "$.findings[1].path")).isNull();
        assertThat(JsonPath.<Object>read(review, "$.findings[1].line")).isNull();
        assertThat(JsonPath.<Object>read(review, "$.findings[1].requirementCode")).isNull();
        assertThat(JsonPath.<String>read(review, "$.findings[1].category")).isEqualTo("MAINTAINABILITY");
        assertThat(JsonPath.<String>read(review, "$.findings[1].taskType")).isEqualTo("BACKEND");
        assertThat(JsonPath.<String>read(review, "$.findings[1].suggestion")).isEqualTo("Fix the problem described above.");
        assertThat(JsonPath.<Integer>read(review, "$.findings[2].line")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(review, "$.counts.high")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(review, "$.counts.open")).isEqualTo(3);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        Mockito.verify(ai, Mockito.atLeastOnce()).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo("review.generate");
        assertThat(request.getValue().maxTokens()).isEqualTo(ReviewAssistant.MAX_OUTPUT_TOKENS);
        assertThat(request.getValue().userMessage())
                .contains("=== " + APP + " ===\n1 | class App {\n2 |   int x;\n3 | }")
                .contains("<tasks>\nT-001 [IN_PROGRESS] Set up the backend\nT-002 [TODO] Implement the invoice list")
                .contains("FR-001 [MUST, General] List invoices");

        String id = JsonPath.read(review, "$.id");
        String high = JsonPath.read(review, "$.findings[0].id");
        String medium = JsonPath.read(review, "$.findings[1].id");

        // A finding becomes a backlog task
        String afterTask = ok(perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/task", projectId, id, high)));
        assertThat(JsonPath.<String>read(afterTask, "$.findings[0].status")).isEqualTo("TASK_CREATED");
        assertThat(JsonPath.<String>read(afterTask, "$.findings[0].taskCode")).isEqualTo("T-003");
        perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/task", projectId, id, high))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A task was already created for this finding (T-003)."));
        String tasks = ok(perform(get("/api/projects/{id}/tasks", projectId)));
        assertThat(JsonPath.<List<String>>read(tasks, "$.tasks[?(@.code == 'T-003')].title")).containsExactly("Check who may list invoices");
        assertThat(JsonPath.<List<String>>read(tasks, "$.tasks[?(@.code == 'T-003')].type")).containsExactly("BACKEND");
        assertThat(JsonPath.<List<String>>read(tasks, "$.tasks[?(@.code == 'T-003')].status")).containsExactly("TODO");
        assertThat(JsonPath.<List<List<String>>>read(tasks, "$.tasks[?(@.code == 'T-003')].requirements").getFirst())
                .containsExactly("FR-001");
        assertThat(JsonPath.<List<String>>read(tasks, "$.tasks[?(@.code == 'T-003')].description").getFirst())
                .contains("Anyone can list them.\n\nSuggested fix: Scope by owner.\n\nFound by the code review in " + APP + ".");

        // Dismiss and reopen
        perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/dismiss", projectId, id, medium))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[1].status").value("DISMISSED"))
                .andExpect(jsonPath("$.counts.dismissed").value(1));
        perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/task", projectId, id, medium))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This finding is dismissed. Reopen it first."));
        perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/reopen", projectId, id, medium))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[1].status").value("OPEN"));
        perform(post("/api/projects/{p}/reviews/{r}/findings/{f}/reopen", projectId, id, medium))
                .andExpect(status().isConflict());

        perform(get("/api/projects/{id}/reviews", projectId))
                .andExpect(jsonPath("$.canReview").value(true))
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].counts.taskCreated").value(1))
                .andExpect(jsonPath("$.latest.id").value(id))
                .andExpect(jsonPath("$.latest.outdated").value(false));

        // Accepting newer code makes the review outdated
        when(ai.complete(any())).thenReturn(reply("""
                {"summary": "Rename.", "files": [
                  {"path": "backend/src/main/java/App.java", "action": "UPDATE", "content": "class App { int count; }", "explanation": "Rename."}]}
                """));
        String code = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(code, "$.id")))
                .andExpect(status().isOk());
        perform(get("/api/projects/{p}/reviews/{r}", projectId, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outdated").value(true));
    }

    @Test
    void otherUsersCannotSeeReviews() throws Exception {
        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/reviews", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{id}/reviews/generate", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    /** Generates the backlog and accepts code for T-001; returns T-001's id. */
    private String acceptedCode() throws Exception {
        when(ai.complete(any())).thenReturn(reply(TASKS));
        String backlog = ok(perform(post("/api/projects/{id}/tasks/generate", projectId)));
        String t1 = JsonPath.<List<String>>read(backlog, "$.tasks[?(@.code == 'T-001')].id").getFirst();
        when(ai.complete(any())).thenReturn(reply("""
                {"summary": "Skeleton.", "files": [
                  {"path": "backend/pom.xml", "action": "CREATE", "content": "<project/>", "explanation": "Build file."},
                  {"path": "backend/src/main/java/App.java", "action": "CREATE", "content": "class App {\\n  int x;\\n}", "explanation": "App."}]}
                """));
        String code = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(code, "$.id")))
                .andExpect(status().isOk());
        return t1;
    }

    private static AiResult reply(String json) {
        return new AiResult(json, "test-model", 100, 50);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static String ok(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        assertThat(actions.andReturn().getResponse().getStatus()).as(body).isEqualTo(200);
        return body;
    }

    /** Requirements, architecture, database design and API design, all approved. */
    private void approvedDesign() throws Exception {
        for (String title : new String[] {"List invoices", "Create an invoice"}) {
            perform(post("/api/projects/{id}/requirements", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "FUNCTIONAL", "category": "General", "title": "%s", "description": "%s.",
                                     "acceptanceCriteria": ["It works"], "priority": "MUST"}
                                    """.formatted(title, title)))
                    .andExpect(status().isCreated());
        }
        for (String[] move : new String[][] {{"DRAFT", "IN_REVIEW"}, {"IN_REVIEW", "APPROVED"}}) {
            perform(post("/api/projects/{id}/requirements/bulk-status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"from\": \"%s\", \"to\": \"%s\"}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
        approveStage("architecture", DesignFixtures.ARCHITECTURE);
        approveStage("database-design", DesignFixtures.DATABASE);
        approveStage("api-design", DesignFixtures.API);
    }

    private void approveStage(String stage, String generated) throws Exception {
        when(ai.complete(any())).thenReturn(reply(generated));
        perform(post("/api/projects/{id}/" + stage + "/generate", projectId)).andExpect(status().isOk());
        for (String[] move : new String[][] {{"IN_REVIEW", "0"}, {"APPROVED", "1"}}) {
            perform(post("/api/projects/{id}/" + stage + "/status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"%s\", \"version\": %s}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
    }
}
