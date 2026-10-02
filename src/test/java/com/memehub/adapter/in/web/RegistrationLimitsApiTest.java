package com.memehub.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Starts its own application with a tiny per-address registration allowance.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "memehub.security.throttling.registrations-per-address=2")
class RegistrationLimitsApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void oneAddressCannotCreateAccountsWithoutLimit() throws Exception {
        mvc.perform(register()).andExpect(status().isCreated());
        mvc.perform(register()).andExpect(status().isCreated());

        mvc.perform(register())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private MockHttpServletRequestBuilder register() throws Exception {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", "limit-" + UUID.randomUUID().toString().substring(0, 8),
                        "password", "a-good-password")));
    }
}
