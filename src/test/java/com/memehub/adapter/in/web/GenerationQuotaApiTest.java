package com.memehub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Starts its own application with small per-user allowances.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "memehub.generation.max-active-per-user=2",
        "memehub.generation.max-per-day-per-user=3"})
class GenerationQuotaApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;

    private record Account(UUID id, String token) {
    }

    @Test
    void aUserCannotSubmitMoreRequestsPerDayThanAllowed() throws Exception {
        Account user = newUser();

        for (int i = 1; i <= 3; i++) {
            String jobId = submit(user, "週一又要上班 " + i).andReturnJobId();
            // Wait for it to finish so only the daily allowance (not the in-progress one) can refuse.
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                    .until(() -> isFinished(jobId, user));
        }

        mvc.perform(post("/api/generations").header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situation\": \"第四次\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void aUserWithTooManyRequestsInProgressMustWait() throws Exception {
        Account user = newUser();
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                    INSERT INTO generation_job (id, requester_id, situation, status, attempts, started_at)
                    VALUES (?, ?, 'in progress', 'RUNNING', 1, now())""", UUID.randomUUID(), user.id);
        }

        mvc.perform(post("/api/generations").header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situation\": \"too soon\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"));
    }

    @Test
    void oneUsersAllowanceDoesNotAffectAnother() throws Exception {
        Account busy = newUser();
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                    INSERT INTO generation_job (id, requester_id, situation, status, attempts, started_at)
                    VALUES (?, ?, 'in progress', 'RUNNING', 1, now())""", UUID.randomUUID(), busy.id);
        }
        Account other = newUser();

        mvc.perform(post("/api/generations").header("Authorization", "Bearer " + other.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situation\": \"沒問題\"}"))
                .andExpect(status().isAccepted());
    }

    // -- helpers ------------------------------------------------------------

    private record Submitted(MvcResult result, ObjectMapper json) {
        String andReturnJobId() throws Exception {
            return json.readTree(result.getResponse().getContentAsString()).get("jobId").asText();
        }
    }

    private Submitted submit(Account user, String situation) throws Exception {
        MvcResult result = mvc.perform(post("/api/generations").header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("situation", situation))))
                .andExpect(status().isAccepted()).andReturn();
        return new Submitted(result, json);
    }

    private boolean isFinished(String jobId, Account user) throws Exception {
        MvcResult result = mvc.perform(get("/api/generations/" + jobId).header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk()).andReturn();
        String state = json.readTree(result.getResponse().getContentAsString()).get("status").asText();
        return state.equals("COMPLETED") || state.equals("FAILED");
    }

    private Account newUser() throws Exception {
        String username = "quota-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        User user = User.register(username, hasher.hash(password), Role.USER);
        users.save(user);
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("token");
        return new Account(user.id(), json.readTree(result.getResponse().getContentAsString()).get("token").asText());
    }
}
