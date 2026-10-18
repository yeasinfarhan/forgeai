package com.forgeai.usage;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiProvider;
import com.forgeai.ai.AiResult;
import com.forgeai.support.DesignFixtures;
import com.forgeai.support.IntegrationTest;

/** Every AI call is recorded per user and project, and the hourly limit is enforced. */
@TestPropertySource(properties = {"forgeai.usage.requests-per-hour=2", "forgeai.usage.tokens-per-day=0"})
class AiUsageIntegrationTest extends IntegrationTest {

    @MockitoBean
    private AiProvider provider;

    private String token;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        when(provider.name()).thenReturn("test");
        token = registerUser();
        projectId = createProject(token, "Invoice Tracker");
        perform(post("/api/projects/{id}/requirements", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FUNCTIONAL", "category": "General", "title": "List invoices", "description": "List.",
                                 "acceptanceCriteria": ["It works"], "priority": "MUST"}
                                """))
                .andExpect(status().isCreated());
        for (String[] move : new String[][] {{"DRAFT", "IN_REVIEW"}, {"IN_REVIEW", "APPROVED"}}) {
            perform(post("/api/projects/{id}/requirements/bulk-status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"from\": \"%s\", \"to\": \"%s\"}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void callsAreRecordedAndTheHourlyLimitIsEnforced() throws Exception {
        perform(get("/api/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limits.requestsPerHour").value(2))
                .andExpect(jsonPath("$.limits.tokensPerDay").value(0))
                .andExpect(jsonPath("$.lastHour.requests").value(0))
                .andExpect(jsonPath("$.days.length()").value(14));

        // A failed call counts as a request, without tokens
        when(provider.complete(any())).thenThrow(new AiException(AiFailure.UNAVAILABLE, "down"));
        perform(post("/api/projects/{id}/architecture/generate", projectId)).andExpect(status().isServiceUnavailable());

        doReturn(new AiResult(DesignFixtures.ARCHITECTURE, "test-model", 1200, 300)).when(provider).complete(any());
        perform(post("/api/projects/{id}/architecture/generate", projectId)).andExpect(status().isOk());

        perform(post("/api/projects/{id}/architecture/generate", projectId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail", containsString("You have reached the limit of 2 AI requests per hour. Try again in")));

        perform(get("/api/usage"))
                .andExpect(jsonPath("$.lastHour.requests").value(2))
                .andExpect(jsonPath("$.lastHour.failed").value(1))
                .andExpect(jsonPath("$.last24Hours.inputTokens").value(1200))
                .andExpect(jsonPath("$.last24Hours.outputTokens").value(300))
                .andExpect(jsonPath("$.days[13].totals.requests").value(2))
                .andExpect(jsonPath("$.purposes[0].purpose").value("architecture.generate"))
                .andExpect(jsonPath("$.projects[0].projectId").value(projectId))
                .andExpect(jsonPath("$.projects[0].projectName").value("Invoice Tracker"));

        // Usage is per user
        String other = registerUser();
        mockMvc.perform(get("/api/usage").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.lastHour.requests").value(0));
    }

    @Test
    void usageNeedsSignIn() throws Exception {
        mockMvc.perform(get("/api/usage")).andExpect(status().isUnauthorized());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
}
