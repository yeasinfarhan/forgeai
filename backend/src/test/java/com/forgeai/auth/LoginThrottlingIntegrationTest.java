package com.forgeai.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.forgeai.support.IntegrationTest;

/** Failed sign-ins for one email are limited (10 per 15 minutes by default). */
class LoginThrottlingIntegrationTest extends IntegrationTest {

    @Test
    void repeatedFailedSignInsAreRefusedEvenWithTheRightPassword() throws Exception {
        String email = "throttle-" + UUID.randomUUID() + "@example.com";
        registerUser(email, "correct-horse-battery");

        for (int i = 0; i < 10; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(email, "correct-horse-battery")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many failed sign-in attempts. Try again in 15 minutes."));

        // Other accounts are not affected
        String other = "other-" + UUID.randomUUID() + "@example.com";
        registerUser(other, "correct-horse-battery");
        login(other, "correct-horse-battery").andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
