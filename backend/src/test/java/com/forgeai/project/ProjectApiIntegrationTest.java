package com.forgeai.project;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.forgeai.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

class ProjectApiIntegrationTest extends IntegrationTest {

    @Test
    void fullLifecycle() throws Exception {
        String token = registerUser();

        // Create
        String created = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Invoice tracker  ", "description": "Track client invoices"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/projects/")))
                .andExpect(jsonPath("$.name").value("Invoice tracker"))
                .andExpect(jsonPath("$.description").value("Track client invoices"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        // Read
        mockMvc.perform(get("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // List
        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        // Update (blank description is stored as no description)
        mockMvc.perform(put("/api/projects/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Invoice tracker v2", "description": "   "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invoice tracker v2"))
                .andExpect(jsonPath("$.description").doesNotExist());

        // Delete
        mockMvc.perform(delete("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIsOrderedByMostRecentlyUpdated() throws Exception {
        String token = registerUser();
        String first = createProject(token, "First");
        String second = createProject(token, "Second");

        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
    }

    @Test
    void usersCannotSeeOrChangeEachOthersProjects() throws Exception {
        String ownerToken = registerUser();
        String otherToken = registerUser();
        String id = createProject(ownerToken, "Private project");

        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/projects/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hijacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        // Still intact for the owner
        mockMvc.perform(get("/api/projects/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private project"));
    }

    @Test
    void createValidatesInput() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void unknownOrMalformedIdsAreHandled() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }
}
