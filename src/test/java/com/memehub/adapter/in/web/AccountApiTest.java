package com.memehub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import com.memehub.adapter.security.SecurityProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AccountApiTest extends IntegrationTestBase {

    private static final String PASSWORD = "a-good-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityProperties securityProperties;

    @Test
    void aNewUserCanSignInButIsNotAnAdministrator() throws Exception {
        String username = uniqueName();

        mvc.perform(postJson("/api/auth/register", username, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.id").exists());

        String token = tokenFor(username, PASSWORD);
        mvc.perform(get("/api/templates/search").param("q", "anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/templates").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE lower(username) = lower(?)",
                String.class, username)).isEqualTo("USER");
    }

    @Test
    void thePasswordIsStoredHashedNeverAsTyped() throws Exception {
        String username = uniqueName();
        mvc.perform(postJson("/api/auth/register", username, PASSWORD)).andExpect(status().isCreated());

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE lower(username) = lower(?)", String.class, username);

        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void aUsernameCannotBeTakenTwiceEvenWithDifferentCapitals() throws Exception {
        String username = uniqueName();
        mvc.perform(postJson("/api/auth/register", username, PASSWORD)).andExpect(status().isCreated());

        mvc.perform(postJson("/api/auth/register", username.toUpperCase(), PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void theAdministratorNameIsAlreadyTaken() throws Exception {
        mvc.perform(postJson("/api/auth/register", securityProperties.bootstrapAdmin().username(), PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void unacceptableUsernamesAndPasswordsAreRefused() throws Exception {
        mvc.perform(postJson("/api/auth/register", "ab", PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/register", "has space", PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/register", uniqueName(), "short")).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/register", "", PASSWORD)).andExpect(status().isBadRequest());
        String name = uniqueName();
        mvc.perform(postJson("/api/auth/register", name, name)).andExpect(status().isBadRequest());
    }

    @Test
    void fiveWrongPasswordsLockThatAccountForThatAddressEvenForTheRightPassword() throws Exception {
        String victim = registered();
        String bystander = registered();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(postJson("/api/auth/login", victim, "wrong-" + attempt)).andExpect(status().isUnauthorized());
        }

        mvc.perform(postJson("/api/auth/login", victim, "wrong-again"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mvc.perform(postJson("/api/auth/login", victim, PASSWORD)).andExpect(status().isTooManyRequests());
        // Another account from the same address is not affected.
        mvc.perform(postJson("/api/auth/login", bystander, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void aSuccessfulSignInStartsTheCountAgain() throws Exception {
        String username = registered();

        for (int attempt = 0; attempt < 4; attempt++) {
            mvc.perform(postJson("/api/auth/login", username, "wrong")).andExpect(status().isUnauthorized());
        }
        mvc.perform(postJson("/api/auth/login", username, PASSWORD)).andExpect(status().isOk());
        for (int attempt = 0; attempt < 4; attempt++) {
            mvc.perform(postJson("/api/auth/login", username, "wrong")).andExpect(status().isUnauthorized());
        }

        mvc.perform(postJson("/api/auth/login", username, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void lockedOutAttemptsLookTheSameForUnknownAccounts() throws Exception {
        String ghost = "ghost-" + UUID.randomUUID();

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(postJson("/api/auth/login", ghost, "wrong")).andExpect(status().isUnauthorized());
        }

        mvc.perform(postJson("/api/auth/login", ghost, "wrong")).andExpect(status().isTooManyRequests());
    }

    // -- helpers ------------------------------------------------------------

    private static String uniqueName() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registered() throws Exception {
        String username = uniqueName();
        mvc.perform(postJson("/api/auth/register", username, PASSWORD)).andExpect(status().isCreated());
        return username;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String url, String username, String password) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private String tokenFor(String username, String password) throws Exception {
        ResultActions result = mvc.perform(postJson("/api/auth/login", username, password)).andExpect(status().isOk());
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}
