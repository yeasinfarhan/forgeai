package com.forgeai.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/**
 * Base class for end-to-end API tests: the full application runs against a real PostgreSQL
 * started by Testcontainers (Docker required), with Flyway applying the real migrations.
 *
 * <p>One container is shared by all test classes and stopped automatically when the JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    /** Registers a new user with a unique email and returns their access token. */
    protected String registerUser() throws Exception {
        return registerUser("user-" + UUID.randomUUID() + "@example.com", "correct-horse-battery");
    }

    protected String registerUser(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s", "displayName": "Test User"}
                """.formatted(email, password);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
