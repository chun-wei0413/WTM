package com.usethatmeme.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.IntegrationTestBase;
import com.usethatmeme.adapter.security.SecurityProperties;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AdminTemplateApiTest extends IntegrationTestBase {

    private static final String PROFILE_JSON = """
            {"meaning": "Rejecting one thing in favor of another",
             "usageExamples": ["skip writing tests, ship it"],
             "emotions": ["smug"], "aliases": ["Drake"]}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        var admin = securityProperties.bootstrapAdmin();
        adminToken = login(admin.username(), admin.password());
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/admin/templates")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUser() throws Exception {
        String body = """
                {"username": "%s", "password": "definitely-wrong"}""";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(securityProperties.bootstrapAdmin().username())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("nobody-" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotUseAdminEndpoints() throws Exception {
        String username = "user-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        users.save(User.register(username, hasher.hash(password), Role.USER));

        String userToken = login(username, password);

        mvc.perform(get("/api/admin/templates").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDraftConfigureApproveAndRetireATemplate() throws Exception {
        String id = draftTemplate("Drake", png(600, 600));

        // The stored image is reachable through a presigned URL.
        JsonNode draft = getTemplate(id);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("imageWidth").asInt()).isEqualTo(600);
        assertThat(download(draft.get("imageUrl").asText())).isNotEmpty();

        // Cannot approve before the profile is complete.
        action(id, "/approve").andExpect(status().isConflict());

        putJson("/api/admin/templates/" + id + "/profile", PROFILE_JSON).andExpect(status().isNoContent());
        postJson("/api/admin/templates/" + id + "/slots", slot(1, 10, 0)).andExpect(status().isNoContent());
        postJson("/api/admin/templates/" + id + "/slots", slot(2, 10, 300)).andExpect(status().isNoContent());
        action(id, "/approve").andExpect(status().isNoContent());

        JsonNode approved = getTemplate(id);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("version").asInt()).isEqualTo(1);
        assertThat(approved.get("slots")).hasSize(2);
        assertThat(approved.get("profile").get("usageExamples").get(0).asText())
                .isEqualTo("skip writing tests, ship it");

        // Changing the layout of an approved template bumps its version.
        putJson("/api/admin/templates/" + id + "/slots/1", slot(1, 8, 0)).andExpect(status().isNoContent());
        assertThat(getTemplate(id).get("version").asInt()).isEqualTo(2);

        action(id, "/approve").andExpect(status().isConflict());

        mvc.perform(get("/api/admin/templates").param("status", "approved")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).exists());

        action(id, "/retire").andExpect(status().isNoContent());
        postJson("/api/admin/templates/" + id + "/slots", slot(3, 10, 0)).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/templates/" + id + "/slots/1").header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void templateWithoutSlotsCanBeApproved() throws Exception {
        String id = draftTemplate("Ready-made reaction", png(300, 200));
        putJson("/api/admin/templates/" + id + "/profile", PROFILE_JSON).andExpect(status().isNoContent());

        action(id, "/approve").andExpect(status().isNoContent());

        assertThat(getTemplate(id).get("slots")).isEmpty();
    }

    @Test
    void rejectsSlotOutsideImage() throws Exception {
        String id = draftTemplate("Small", png(100, 100));

        postJson("/api/admin/templates/" + id + "/slots", slot(1, 10, 90)).andExpect(status().isConflict());
    }

    @Test
    void rejectsFileThatIsNotAnImage() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/api/admin/templates").file(file).param("name", "Bad")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTemplateIsNotFound() throws Exception {
        mvc.perform(get("/api/admin/templates/" + UUID.randomUUID()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // -- helpers ------------------------------------------------------------

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }

    private String draftTemplate(String name, byte[] image) throws Exception {
        var file = new MockMultipartFile("file", "template.png", "image/png", image);
        MvcResult result = mvc.perform(multipart("/api/admin/templates").file(file).param("name", name)
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode getTemplate(String id) throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/templates/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions action(String id, String action) throws Exception {
        return mvc.perform(post("/api/admin/templates/" + id + action).header("Authorization", bearer()));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String url, String body) throws Exception {
        return mvc.perform(put(url).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String slot(int slotNo, int maxChars, int y) {
        return """
                {"slotNo": %d, "role": "role-%d", "maxChars": %d, "required": true,
                 "x": 0, "y": %d, "width": 100, "height": 50}""".formatted(slotNo, slotNo, maxChars, y);
    }

    private static byte[] png(int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] download(String url) throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
