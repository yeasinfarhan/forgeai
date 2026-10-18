package com.forgeai.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import com.forgeai.support.DesignFixtures;
import com.forgeai.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** Code generation and review end to end, with the AI provider mocked and the design approved. */
class CodeApiIntegrationTest extends IntegrationTest {

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

    /** Deliberately imperfect: unsafe paths, a secret file, a duplicate, a fence, a delete of nothing. */
    private static final String CODE = """
            {"summary": "Project skeleton.", "files": [
              {"path": "./backend/pom.xml", "action": "UPDATE", "content": "<project/>", "explanation": "Build file."},
              {"path": "README.md", "action": "CREATE", "content": "```markdown\\n# Invoices\\n```", "explanation": ""},
              {"path": "../etc/passwd", "action": "CREATE", "content": "x", "explanation": "Outside the project."},
              {"path": "backend/.env", "action": "CREATE", "content": "SECRET=1", "explanation": "A secret."},
              {"path": "README.md", "action": "CREATE", "content": "duplicate", "explanation": "Duplicate."},
              {"path": "old.txt", "action": "DELETE", "content": "", "explanation": "Does not exist."}
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
    void codeNeedsTasksAndFinishedDependencies() throws Exception {
        approvedDesign();
        perform(get("/api/projects/{id}/code", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.blocker").value("Generate the development tasks first."));

        String backlog = generateTasks();
        perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, taskId(backlog, "T-002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("T-002 is waiting for T-001 to be done")));
        perform(get("/api/projects/{id}/code", projectId))
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.tasks.length()").value(2))
                .andExpect(jsonPath("$.tasks[1].blockedBy[0]").value("T-001"));
    }

    @Test
    void proposalsAreRepairedReviewedFileByFileAndExported() throws Exception {
        approvedDesign();
        String backlog = generateTasks();
        String t1 = taskId(backlog, "T-001");

        when(ai.complete(any())).thenReturn(reply(CODE));
        String changeset = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        assertThat(JsonPath.<String>read(changeset, "$.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(changeset, "$.taskCode")).isEqualTo("T-001");
        assertThat(JsonPath.<List<String>>read(changeset, "$.changes[*].path")).containsExactly("backend/pom.xml", "README.md");
        assertThat(JsonPath.<List<String>>read(changeset, "$.changes[*].action")).containsExactly("CREATE", "CREATE");
        assertThat(JsonPath.<String>read(changeset, "$.changes[0].newContent")).isEqualTo("<project/>\n");
        assertThat(JsonPath.<String>read(changeset, "$.changes[1].newContent")).isEqualTo("# Invoices\n");
        assertThat(JsonPath.<String>read(changeset, "$.changes[1].explanation")).isEqualTo("Changed for this task.");

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        org.mockito.Mockito.verify(ai, org.mockito.Mockito.atLeastOnce()).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo("code.generate");
        assertThat(request.getValue().maxTokens()).isEqualTo(CodeAssistant.MAX_OUTPUT_TOKENS);
        assertThat(request.getValue().userMessage())
                .contains("T-001 [SETUP] Set up the backend")
                .contains("- GET /invoices: List invoices")
                .contains("<workspace>\n(empty: this is the first code of the project)");

        // Generating code starts the task
        perform(get("/api/projects/{id}/tasks", projectId)).andExpect(jsonPath("$.tasks[0].status").value("IN_PROGRESS"));

        String id = JsonPath.read(changeset, "$.id");
        String pom = JsonPath.read(changeset, "$.changes[0].id");
        String readme = JsonPath.read(changeset, "$.changes[1].id");
        perform(post("/api/projects/{p}/code/changesets/{c}/changes/{f}/accept", projectId, id, pom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        perform(get("/api/projects/{id}/code/file", projectId).param("path", "backend/pom.xml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<project/>\n"))
                .andExpect(jsonPath("$.lastTaskCode").value("T-001"));
        perform(post("/api/projects/{p}/code/changesets/{c}/changes/{f}/reject", projectId, id, readme))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[1].decision").value("REJECTED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        perform(post("/api/projects/{p}/code/changesets/{c}/changes/{f}/accept", projectId, id, readme))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This change set is completed; nothing is left to review."));
        perform(get("/api/projects/{id}/code/file", projectId).param("path", "README.md")).andExpect(status().isNotFound());

        perform(get("/api/projects/{id}/code", projectId))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].path").value("backend/pom.xml"))
                .andExpect(jsonPath("$.files[0].lines").value(1))
                .andExpect(jsonPath("$.changesets[0].accepted").value(1))
                .andExpect(jsonPath("$.changesets[0].rejected").value(1))
                .andExpect(jsonPath("$.tasks[0].pendingChangesetId").doesNotExist());

        byte[] zip = perform(get("/api/projects/{id}/code/export.zip", projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-tracker.zip")))
                .andReturn().getResponse().getContentAsByteArray();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.add(entry.getName() + "=" + new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsExactly("invoice-tracker/backend/pom.xml=<project/>\n");
    }

    @Test
    void staleProposalsCannotBeAcceptedAndNewerOnesReplaceOlder() throws Exception {
        approvedDesign();
        String backlog = generateTasks();
        String t1 = taskId(backlog, "T-001");
        String t2 = taskId(backlog, "T-002");

        // A: superseded by B before review
        when(ai.complete(any())).thenReturn(reply(pom("<a/>")));
        String a = JsonPath.read(ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1))), "$.id");
        when(ai.complete(any())).thenReturn(reply(pom("<b/>")));
        String b = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        perform(get("/api/projects/{p}/code/changesets/{c}", projectId, a)).andExpect(jsonPath("$.status").value("DISCARDED"));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(b, "$.id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // C proposes an update of the file for T-001; D (T-002) changes the file first
        when(ai.complete(any())).thenReturn(reply(pom("<c/>")));
        String c = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        assertThat(JsonPath.<String>read(c, "$.changes[0].action")).isEqualTo("UPDATE");
        assertThat(JsonPath.<String>read(c, "$.changes[0].baseContent")).isEqualTo("<b/>\n");

        String tasks = ok(perform(get("/api/projects/{id}/tasks", projectId)));
        int version = JsonPath.<List<Integer>>read(tasks, "$.tasks[?(@.code == 'T-001')].version").getFirst();
        perform(post("/api/projects/{p}/tasks/{t}/status", projectId, t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DONE\", \"version\": %d}".formatted(version)))
                .andExpect(status().isOk());
        when(ai.complete(any())).thenReturn(reply(pom("<d/>")));
        String d = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t2)));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(d, "$.id")))
                .andExpect(status().isOk());

        String cId = JsonPath.read(c, "$.id");
        perform(get("/api/projects/{p}/code/changesets/{c}", projectId, cId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.changes[0].stale").value(true));
        perform(post("/api/projects/{p}/code/changesets/{c}/changes/{f}/accept", projectId, cId, JsonPath.<String>read(c, "$.changes[0].id")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "backend/pom.xml changed after this proposal was made. Reject it and generate the task's code again."));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, cId)).andExpect(status().isConflict());
        perform(post("/api/projects/{p}/code/changesets/{c}/discard", projectId, cId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
        perform(get("/api/projects/{id}/code/file", projectId).param("path", "backend/pom.xml"))
                .andExpect(jsonPath("$.content").value("<d/>\n"))
                .andExpect(jsonPath("$.lastTaskCode").value("T-002"));
    }

    @Test
    void otherUsersCannotSeeTheCode() throws Exception {
        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/code", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{id}/code/export.zip", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private static String pom(String content) {
        return """
                {"summary": "Build file.", "files": [
                  {"path": "backend/pom.xml", "action": "CREATE", "content": "%s", "explanation": "Build file."}]}
                """.formatted(content);
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

    private static String taskId(String backlog, String code) {
        return JsonPath.<List<String>>read(backlog, "$.tasks[?(@.code == '" + code + "')].id").getFirst();
    }

    private String generateTasks() throws Exception {
        when(ai.complete(any())).thenReturn(reply(TASKS));
        return ok(perform(post("/api/projects/{id}/tasks/generate", projectId)));
    }

    /** Requirements, architecture, database design and API design, all approved. */
    private void approvedDesign() throws Exception {
        for (String[] r : new String[][] {{"FUNCTIONAL", "List invoices"}, {"FUNCTIONAL", "Create an invoice"}}) {
            perform(post("/api/projects/{id}/requirements", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "%s", "category": "General", "title": "%s", "description": "%s.",
                                     "acceptanceCriteria": ["It works"], "priority": "MUST"}
                                    """.formatted(r[0], r[1], r[1])))
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
