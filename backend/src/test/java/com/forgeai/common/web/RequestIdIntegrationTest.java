package com.forgeai.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;

import com.forgeai.support.IntegrationTest;

class RequestIdIntegrationTest extends IntegrationTest {

    @Test
    void everyResponseCarriesARequestIdAndSafeIncomingIdsAreKept() throws Exception {
        mockMvc.perform(get("/api/health").header(RequestLoggingFilter.HEADER, "client-42.a_b"))
                .andExpect(header().string(RequestLoggingFilter.HEADER, "client-42.a_b"));

        String generated = mockMvc.perform(get("/api/projects").header(RequestLoggingFilter.HEADER, "bad id\nInjected: yes"))
                .andReturn().getResponse().getHeader(RequestLoggingFilter.HEADER);
        assertThat(generated).matches("[0-9a-f-]{36}");
    }
}
