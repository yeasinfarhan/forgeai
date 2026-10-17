package com.forgeai.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** Idea intake end to end, with the AI provider replaced by a mock so no API key or credit is needed. */
class IdeaIntakeApiIntegrationTest extends IntegrationTest {

    private static final String QUESTIONS = """
            {"decision": "ask_questions",
             "questions": [
               {"question": "Who will use the app?", "rationale": "Decides the main workflows."},
               {"question": "Web, mobile or both?", "rationale": "Decides the platform."}
             ],
             "brief": null}
            """;

    private static final String BRIEF_FIELDS = """
            "title": "Invoice Tracker",
            "summary": "Freelancers create invoices and track payments.",
            "problem": "Freelancers lose track of unpaid invoices.",
            "targetUsers": ["Freelancers"],
            "goals": ["Get paid on time"],
            "coreFeatures": [{"name": "Invoices", "description": "Create and send invoices."}],
            "outOfScope": ["Accounting"],
            "constraints": [],
            "openQuestions": ["Which currencies?"]
            """;

    private static final String BRIEF_TURN = "{\"decision\": \"write_brief\", \"questions\": [], \"brief\": {" + BRIEF_FIELDS + "}}";
    private static final String BRIEF_ONLY = "{" + BRIEF_FIELDS + "}";

    @MockitoBean
    private AiClient claude;

    private String token;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        projectId = createProject(token, "Invoice tracker");
    }

    private static AiResult reply(String json) {
        return new AiResult(json, "claude-sonnet-5-5", 100, 50);
    }

    @Test
    void notStartedBeforeAnyIdea() throws Exception {
        perform(get("/api/projects/{id}/intake", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.maxQuestionRounds").value(3));
        verifyNoInteractions(claude);
    }

    @Test
    void ideaThenQuestionsThenBrief() throws Exception {
        when(claude.complete(any())).thenReturn(reply(QUESTIONS), reply(BRIEF_TURN));

        String started = perform(post("/api/projects/{id}/intake", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\": \"An app for freelancers to send invoices.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_ANSWERS"))
                .andExpect(jsonPath("$.currentRound").value(1))
                .andExpect(jsonPath("$.idea").value("An app for freelancers to send invoices."))
                .andExpect(jsonPath("$.rounds[0].questions.length()").value(2))
                .andExpect(jsonPath("$.rounds[0].questions[0].question").value("Who will use the app?"))
                .andReturn().getResponse().getContentAsString();
        String q1 = JsonPath.read(started, "$.rounds[0].questions[0].id");
        String q2 = JsonPath.read(started, "$.rounds[0].questions[1].id");

        perform(post("/api/projects/{id}/intake/answers", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers": [
                                  {"questionId": "%s", "answer": "  Freelance designers  "},
                                  {"questionId": "%s", "answer": ""}
                                ]}
                                """.formatted(q1, q2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BRIEF_READY"))
                .andExpect(jsonPath("$.currentRound").doesNotExist())
                .andExpect(jsonPath("$.brief.title").value("Invoice Tracker"))
                .andExpect(jsonPath("$.brief.coreFeatures[0].name").value("Invoices"))
                .andExpect(jsonPath("$.briefModel").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.rounds[0].questions[0].answer").value("Freelance designers"))
                .andExpect(jsonPath("$.rounds[0].questions[1].answer").doesNotExist());

        // The second prompt carried the project, the idea and the answers (a blank one as skipped).
        ArgumentCaptor<AiRequest> requests = ArgumentCaptor.forClass(AiRequest.class);
        verify(claude, times(2)).complete(requests.capture());
        AiRequest second = requests.getAllValues().get(1);
        assertThat(second.purpose()).isEqualTo("idea-intake.turn");
        assertThat(second.jsonSchema()).isEqualTo(IntakeSchemas.TURN);
        assertThat(second.userMessage())
                .contains("Name: Invoice tracker")
                .contains("An app for freelancers to send invoices.")
                .contains("Q: Who will use the app?\nA: Freelance designers")
                .contains("Q: Web, mobile or both?\nA: (skipped)");

        // Persisted: reading it back gives the same state.
        perform(get("/api/projects/{id}/intake", projectId))
                .andExpect(jsonPath("$.status").value("BRIEF_READY"))
                .andExpect(jsonPath("$.brief.openQuestions[0]").value("Which currencies?"));

        // No open questions any more.
        perform(post("/api/projects/{id}/intake/answers", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": []}"))
                .andExpect(status().isConflict());
    }

    @Test
    void writeBriefNowSkipsRemainingQuestions() throws Exception {
        when(claude.complete(any())).thenReturn(reply(QUESTIONS), reply(BRIEF_ONLY));
        startIntake();

        perform(post("/api/projects/{id}/intake/brief", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BRIEF_READY"))
                .andExpect(jsonPath("$.brief.title").value("Invoice Tracker"));

        ArgumentCaptor<AiRequest> requests = ArgumentCaptor.forClass(AiRequest.class);
        verify(claude, times(2)).complete(requests.capture());
        assertThat(requests.getValue().purpose()).isEqualTo("idea-intake.brief");
        assertThat(requests.getValue().jsonSchema()).isEqualTo(IntakeSchemas.BRIEF);
    }

    @Test
    void afterTheLastRoundTheBriefIsForced() throws Exception {
        when(claude.complete(any()))
                .thenReturn(reply(QUESTIONS), reply(QUESTIONS), reply(QUESTIONS), reply(BRIEF_ONLY));
        startIntake();
        answerCurrentRound().andExpect(jsonPath("$.currentRound").value(2));
        answerCurrentRound().andExpect(jsonPath("$.currentRound").value(3));
        answerCurrentRound()
                .andExpect(jsonPath("$.status").value("BRIEF_READY"))
                .andExpect(jsonPath("$.rounds.length()").value(3));

        ArgumentCaptor<AiRequest> requests = ArgumentCaptor.forClass(AiRequest.class);
        verify(claude, times(4)).complete(requests.capture());
        assertThat(requests.getValue().purpose()).isEqualTo("idea-intake.brief");
    }

    @Test
    void restartingReplacesThePreviousIntake() throws Exception {
        when(claude.complete(any())).thenReturn(reply(BRIEF_TURN), reply(QUESTIONS));
        startIntake().andExpect(jsonPath("$.status").value("BRIEF_READY"));

        perform(post("/api/projects/{id}/intake", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\": \"Actually, a mobile app.\"}"))
                .andExpect(jsonPath("$.status").value("AWAITING_ANSWERS"))
                .andExpect(jsonPath("$.idea").value("Actually, a mobile app."))
                .andExpect(jsonPath("$.brief").doesNotExist())
                .andExpect(jsonPath("$.rounds.length()").value(1));
    }

    @Test
    void aiFailuresBecomeClearErrorsAndChangeNothing() throws Exception {
        when(claude.complete(any())).thenThrow(new AiException(AiFailure.NOT_CONFIGURED, "no key"));

        startIntake()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.detail", containsString("CLAUDE_API_KEY")));

        perform(get("/api/projects/{id}/intake", projectId))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    void malformedAiOutputIsReportedAsBadGateway() throws Exception {
        when(claude.complete(any())).thenReturn(reply("this is not json"));

        startIntake()
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("BAD_RESPONSE"));
    }

    @Test
    void answersForUnknownQuestionsAreRejected() throws Exception {
        when(claude.complete(any())).thenReturn(reply(QUESTIONS));
        startIntake();

        perform(post("/api/projects/{id}/intake/answers", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": \"%s\", \"answer\": \"x\"}]}".formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
        verify(claude, times(1)).complete(any());
    }

    @Test
    void answeringBeforeStartingIsRejected() throws Exception {
        perform(post("/api/projects/{id}/intake/answers", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": []}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Describe your idea first."));
    }

    @Test
    void ideaIsValidated() throws Exception {
        perform(post("/api/projects/{id}/intake", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\": \"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(claude);
    }

    @Test
    void otherUsersCannotSeeOrDriveTheIntake() throws Exception {
        String otherToken = registerUser();

        mockMvc.perform(get("/api/projects/{id}/intake", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{id}/intake", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\": \"Hijack\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(claude);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions startIntake() throws Exception {
        return perform(post("/api/projects/{id}/intake", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idea\": \"An app for freelancers to send invoices.\"}"));
    }

    private ResultActions answerCurrentRound() throws Exception {
        String state = perform(get("/api/projects/{id}/intake", projectId)).andReturn().getResponse().getContentAsString();
        int round = JsonPath.read(state, "$.currentRound");
        List<String> ids = JsonPath.read(state, "$.rounds[" + (round - 1) + "].questions[*].id");
        String answers = ids.stream()
                .map(id -> "{\"questionId\": \"%s\", \"answer\": \"Answer\"}".formatted(id))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return perform(post("/api/projects/{id}/intake/answers", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": [" + answers + "]}"));
    }
}
