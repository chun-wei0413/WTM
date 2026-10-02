package com.usethatmeme.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.IntegrationTestBase;
import com.usethatmeme.adapter.security.SecurityProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A private installation: nobody can sign up, but the administrator can still sign in.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "usethatmeme.security.registration-enabled=false")
class RegistrationClosedApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;

    @Test
    void signingUpIsRefusedWhileSigningInStillWorks() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "newcomer", "password", "a-good-password"))))
                .andExpect(status().isForbidden());

        var admin = securityProperties.bootstrapAdmin();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", admin.username(), "password", admin.password()))))
                .andExpect(status().isOk());
    }
}
