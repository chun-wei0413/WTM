package com.usethatmeme.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.IntegrationTestBase;
import com.usethatmeme.adapter.security.SecurityProperties;
import com.usethatmeme.application.generation.GenerationQueueHandler;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class GenerationApiTest extends IntegrationTestBase {

    private static final int SLOT_MAX_CHARS = 12;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired GenerationQueueHandler queue;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        var admin = securityProperties.bootstrapAdmin();
        adminToken = login(admin.username(), admin.password());
    }

    @Test
    void aUserGetsCandidateMemesAndCanKeepOne() throws Exception {
        String marker = "mk" + UUID.randomUUID().toString().replace("-", "");
        approvedTemplate("Two slots " + marker, marker, 2);
        approvedTemplate("One slot " + marker, marker, 1);
        approvedTemplate("No slots " + marker, marker, 0);
        mvc.perform(post("/api/admin/index/sync").header("Authorization", bearer(adminToken))).andExpect(status().isOk());

        Account user = newUser();
        MvcResult accepted = mvc.perform(post("/api/generations").header("Authorization", bearer(user.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("situation", "我遇到狀況 " + marker))))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andReturn();
        String jobId = json.readTree(accepted.getResponse().getContentAsString()).get("jobId").asText();

        JsonNode done = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getGeneration(jobId, user.token), j -> isFinished(j));

        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        JsonNode candidates = done.get("candidates");
        assertThat(candidates.size()).isBetween(1, 3);
        List<String> names = new ArrayList<>();
        for (JsonNode candidate : candidates) {
            names.add(candidate.get("templateName").asText());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(download(candidate.get("imageUrl").asText())));
            assertThat(image).as("candidate image decodes").isNotNull();
            assertThat(image.getWidth()).isEqualTo(600);
            candidate.get("captions").fields().forEachRemaining(
                    e -> assertThat(e.getValue().asText().codePointCount(0, e.getValue().asText().length()))
                            .isLessThanOrEqualTo(SLOT_MAX_CHARS));
            if (candidate.get("templateName").asText().startsWith("No slots")) {
                assertThat(candidate.get("captions")).isEmpty();
            }
        }
        assertThat(names).anyMatch(n -> n.contains(marker));

        String memeId = candidates.get(0).get("memeId").asText();
        assertThat(candidates.get(0).get("status").asText()).isEqualTo("COMPOSED");
        mvc.perform(post("/api/memes/" + memeId + "/keep").header("Authorization", bearer(user.token)))
                .andExpect(status().isNoContent());
        assertThat(getGeneration(jobId, user.token).get("candidates").get(0).get("status").asText()).isEqualTo("KEPT");

        JsonNode kept = listMemes(user.token, "");
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).get("id").asText()).isEqualTo(memeId);
        assertThat(kept.get(0).get("status").asText()).isEqualTo("KEPT");
        assertThat(kept.get(0).get("templateName").asText()).isEqualTo(candidates.get(0).get("templateName").asText());
        assertThat(kept.get(0).get("captions")).isEqualTo(candidates.get(0).get("captions"));
        assertThat(download(kept.get(0).get("imageUrl").asText())).isNotEmpty();
        // The owner can download the finished image through the application.
        MvcResult downloaded = mvc.perform(get("/api/memes/" + memeId + "/image")
                        .header("Authorization", bearer(user.token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        assertThat(ImageIO.read(new ByteArrayInputStream(downloaded.getResponse().getContentAsByteArray()))).isNotNull();
        // Someone else cannot, and the answer does not reveal that the meme exists.
        mvc.perform(get("/api/memes/" + memeId + "/image").header("Authorization", bearer(newUser().token)))
                .andExpect(status().isNotFound());
        // The candidates that were not kept are still there, under the other status.
        assertThat(listMemes(user.token, "?status=COMPOSED")).hasSize(candidates.size() - 1);
    }

    @Test
    void aUserOnlySeesTheirOwnMemesAndBadParametersAreRefused() throws Exception {
        Account owner = newUser();
        Account other = newUser();
        String jobId = submit(owner, "週一又要上班");
        JsonNode done = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getGeneration(jobId, owner.token), GenerationApiTest::isFinished);
        if (done.get("candidates").size() > 0) {
            mvc.perform(post("/api/memes/" + done.get("candidates").get(0).get("memeId").asText() + "/keep")
                    .header("Authorization", "Bearer " + owner.token)).andExpect(status().isNoContent());
            assertThat(listMemes(owner.token, "")).hasSize(1);
        }

        assertThat(listMemes(other.token, "")).isEmpty();
        assertThat(listMemes(other.token, "?status=COMPOSED")).isEmpty();
        mvc.perform(get("/api/memes").param("status", "bogus").header("Authorization", "Bearer " + owner.token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/memes").param("limit", "0").header("Authorization", "Bearer " + owner.token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/memes")).andExpect(status().isUnauthorized());
    }

    @Test
    void jobsAndMemesBelongToTheirOwner() throws Exception {
        Account owner = newUser();
        Account other = newUser();
        String jobId = submit(owner, "週一又要上班");
        String memeId = null;

        mvc.perform(get("/api/generations/" + jobId).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/generations/" + jobId).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());

        JsonNode done = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getGeneration(jobId, owner.token), GenerationApiTest::isFinished);
        if (done.get("candidates").size() > 0) {
            memeId = done.get("candidates").get(0).get("memeId").asText();
            mvc.perform(post("/api/memes/" + memeId + "/keep").header("Authorization", bearer(other.token)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void requestsAreValidatedAndAuthenticated() throws Exception {
        Account user = newUser();

        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"situation\": \"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/generations").header("Authorization", bearer(user.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situation\": \"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/generations").header("Authorization", bearer(user.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("situation", "字".repeat(301)))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/generations/" + UUID.randomUUID()).header("Authorization", bearer(user.token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void jobsStuckInRunningAreGivenUpOnAfterTheirLastAttempt() throws Exception {
        Account user = newUser();
        UUID stuck = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO generation_job (id, requester_id, situation, status, attempts, started_at)
                VALUES (?, ?::uuid, 'stuck', 'RUNNING', 3, now() - interval '1 hour')""", stuck, user.id.toString());

        int touched = queue.recoverStale(Duration.ofMinutes(5), 3);

        assertThat(touched).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, failure_reason FROM generation_job WHERE id = ?", stuck);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_reason")).isNotNull();
    }

    // -- helpers ------------------------------------------------------------

    private record Account(UUID id, String token) {
    }

    private Account newUser() throws Exception {
        String username = "maker-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        User user = User.register(username, hasher.hash(password), Role.USER);
        users.save(user);
        return new Account(user.id(), login(username, password));
    }

    private String submit(Account user, String situation) throws Exception {
        MvcResult result = mvc.perform(post("/api/generations").header("Authorization", bearer(user.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("situation", situation))))
                .andExpect(status().isAccepted()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("jobId").asText();
    }

    /** @param query empty, or a query string such as {@code ?status=COMPOSED} */
    private JsonNode listMemes(String token, String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/memes" + query).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getGeneration(String jobId, String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/generations/" + jobId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static boolean isFinished(JsonNode job) {
        String status = job.get("status").asText();
        return status.equals("COMPLETED") || status.equals("FAILED");
    }

    /** Creates, configures and approves a 600x600 template whose usage example contains the marker. */
    private void approvedTemplate(String name, String marker, int slotCount) throws Exception {
        var file = new MockMultipartFile("file", "t.png", "image/png", png());
        MvcResult created = mvc.perform(multipart("/api/admin/templates").file(file).param("name", name)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();

        admin(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/admin/templates/" + id + "/profile"),
                json.writeValueAsString(Map.of("meaning", "測試用的梗", "usageExamples", List.of("我遇到狀況 " + marker),
                        "emotions", List.of(), "aliases", List.of()))).andExpect(status().isNoContent());
        for (int slot = 1; slot <= slotCount; slot++) {
            admin(post("/api/admin/templates/" + id + "/slots"), """
                    {"slotNo": %d, "role": "格子%d", "maxChars": %d, "required": true,
                     "x": 10, "y": %d, "width": 400, "height": 100}""".formatted(slot, slot, SLOT_MAX_CHARS, 10 + (slot - 1) * 150))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/admin/templates/" + id + "/approve").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    private ResultActions admin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                String body) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] download(String url) throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
