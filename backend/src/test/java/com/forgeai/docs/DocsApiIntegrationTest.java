package com.forgeai.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

/** Documentation and the project package end to end, with the AI provider mocked. */
class DocsApiIntegrationTest extends IntegrationTest {

    private static final String TASKS = """
            {"tasks": [
              {"ref": "N1", "title": "Set up the backend", "description": "Skeleton.", "type": "SETUP", "estimate": 2,
               "acceptanceCriteria": ["It builds"], "dependsOn": [], "requirements": [], "entities": [], "endpoints": [],
               "components": ["API"]}
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
    void documentationNeedsTheApprovedDesign() throws Exception {
        perform(get("/api/projects/{id}/docs", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.blocker").isNotEmpty())
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].kind").value("README"))
                .andExpect(jsonPath("$.documents[0].generated").value(false))
                .andExpect(jsonPath("$.entries.length()").value(0));
        perform(post("/api/projects/{id}/docs/documents/README/generate", projectId)).andExpect(status().isConflict());
        perform(get("/api/projects/{id}/docs/package.zip", projectId)).andExpect(status().isConflict());
        perform(get("/api/projects/{id}/docs/documents/README", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The README has not been written yet."));
    }

    @Test
    void documentsAreWrittenEditedAndPackagedWithTheCode() throws Exception {
        approvedDesign();
        String t1 = acceptedCode("<project/>");

        perform(get("/api/projects/{id}/docs", projectId))
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.fileName").value("invoice-tracker-package.zip"))
                .andExpect(jsonPath("$.entries[0].path").value("README.md"))
                .andExpect(jsonPath("$.entries[0].source").value("CODE"))
                .andExpect(jsonPath("$.entries[?(@.path == 'docs/api-reference.md')].source").value("DESIGN"));

        when(ai.complete(any())).thenReturn(reply("{\"content\": \"```markdown\\n# Invoice Tracker\\n\\nTrack invoices.\\n```\"}"));
        String readme = ok(perform(post("/api/projects/{id}/docs/documents/README/generate", projectId)));
        assertThat(JsonPath.<String>read(readme, "$.content")).isEqualTo("# Invoice Tracker\n\nTrack invoices.\n");
        assertThat(JsonPath.<Boolean>read(readme, "$.edited")).isFalse();
        assertThat(JsonPath.<Boolean>read(readme, "$.outdated")).isFalse();

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        Mockito.verify(ai, Mockito.atLeastOnce()).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo("docs.readme");
        assertThat(request.getValue().maxTokens()).isEqualTo(DocsAssistant.MAX_OUTPUT_TOKENS);
        assertThat(request.getValue().userMessage())
                .contains("<documents>\ndocs/developer-guide.md\ndocs/requirements.md\ndocs/architecture.md")
                .contains("docs/backlog.md\n</documents>")
                .contains("=== backend/pom.xml ===\n<project/>")
                .contains("FR-001 [MUST, General] List invoices");

        perform(get("/api/projects/{id}/docs", projectId))
                .andExpect(jsonPath("$.documents[0].generated").value(true))
                .andExpect(jsonPath("$.entries[0].path").value("README.md"))
                .andExpect(jsonPath("$.entries[0].source").value("DOCUMENT"))
                .andExpect(jsonPath("$.entries[0].replacesCode").value(true));

        // Edit by hand, with optimistic locking
        int version = JsonPath.read(readme, "$.version");
        perform(put("/api/projects/{id}/docs/documents/README", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"# Edited\", \"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# Edited\n"))
                .andExpect(jsonPath("$.edited").value(true));
        perform(put("/api/projects/{id}/docs/documents/README", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"# Again\", \"version\": %d}".formatted(version)))
                .andExpect(status().isConflict());

        perform(get("/api/projects/{id}/docs/package/file", projectId).param("path", "docs/api-reference.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DESIGN"))
                .andExpect(jsonPath("$.content", containsString("### GET /api/invoices")));
        perform(get("/api/projects/{id}/docs/package/file", projectId).param("path", "nope.md")).andExpect(status().isNotFound());

        byte[] zip = perform(get("/api/projects/{id}/docs/package.zip", projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-tracker-package.zip")))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries.keySet()).containsExactly(
                "invoice-tracker/README.md",
                "invoice-tracker/backend/pom.xml",
                "invoice-tracker/docs/api-reference.md",
                "invoice-tracker/docs/architecture.md",
                "invoice-tracker/docs/backlog.md",
                "invoice-tracker/docs/data-model.md",
                "invoice-tracker/docs/database.sql",
                "invoice-tracker/docs/openapi.json",
                "invoice-tracker/docs/requirements.md");
        assertThat(entries.get("invoice-tracker/README.md")).isEqualTo("# Edited\n");
        assertThat(entries.get("invoice-tracker/docs/database.sql")).contains("CREATE TABLE \"invoices\"");
        assertThat(entries.get("invoice-tracker/docs/openapi.json")).contains("\"openapi\"");
        assertThat(entries.get("invoice-tracker/docs/requirements.md")).contains("### FR-001 · List invoices");
        assertThat(entries.get("invoice-tracker/docs/backlog.md")).contains("| T-001 | Set up the backend | Setup | In progress |");

        // Accepting newer code makes the README outdated
        when(ai.complete(any())).thenReturn(reply(code("<project><name>x</name></project>")));
        String changeset = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(changeset, "$.id")))
                .andExpect(status().isOk());
        perform(get("/api/projects/{id}/docs", projectId)).andExpect(jsonPath("$.documents[0].outdated").value(true));
    }

    @Test
    void otherUsersCannotSeeTheDocs() throws Exception {
        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/docs", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{id}/docs/package.zip", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private static String code(String pom) {
        return """
                {"summary": "Skeleton.", "files": [
                  {"path": "backend/pom.xml", "action": "CREATE", "content": "%s", "explanation": "Build file."},
                  {"path": "README.md", "action": "CREATE", "content": "# Old", "explanation": "Readme."}]}
                """.formatted(pom);
    }

    /** Generates the backlog and accepts code for T-001; returns T-001's id. */
    private String acceptedCode(String pom) throws Exception {
        when(ai.complete(any())).thenReturn(reply(TASKS));
        String backlog = ok(perform(post("/api/projects/{id}/tasks/generate", projectId)));
        String t1 = JsonPath.<List<String>>read(backlog, "$.tasks[?(@.code == 'T-001')].id").getFirst();
        when(ai.complete(any())).thenReturn(reply(code(pom)));
        String changeset = ok(perform(post("/api/projects/{p}/code/tasks/{t}/generate", projectId, t1)));
        perform(post("/api/projects/{p}/code/changesets/{c}/accept-all", projectId, JsonPath.<String>read(changeset, "$.id")))
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
