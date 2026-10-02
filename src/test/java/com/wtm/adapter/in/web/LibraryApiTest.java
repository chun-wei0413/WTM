package com.wtm.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.IntegrationTestBase;
import com.wtm.adapter.security.SecurityProperties;
import com.wtm.application.port.out.PasswordHasher;
import com.wtm.application.port.out.UserRepository;
import com.wtm.domain.user.Role;
import com.wtm.domain.user.User;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class LibraryApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;

    @Test
    void collectedPicturesAreDeduplicatedTaggedPublishedAndFindable() throws Exception {
        String admin = adminToken();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        BufferedImage alpha = picture(800, 600, false);
        BufferedImage beta = picture(800, 600, true);

        MockMultipartFile[] files = {
                file("alpha-" + unique + ".png", png(alpha)),
                file("beta-" + unique + ".png", png(beta)),
                file("alpha-again-" + unique + ".png", png(alpha)),                       // the same bytes
                file("alpha-small-" + unique + ".jpg", jpeg(scaled(alpha, 400, 300))),    // the same picture, resized
                file("notes-" + unique + ".txt", "not a picture".getBytes()),
                file("icon-" + unique + ".png", png(picture(48, 48, false))),
        };
        var request = multipart("/api/admin/collection/files");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        MvcResult uploaded = mvc.perform(request.header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn();

        JsonNode results = json.readTree(uploaded.getResponse().getContentAsString());
        assertThat(statuses(results)).containsExactly("IMPORTED", "IMPORTED", "DUPLICATE", "DUPLICATE", "REJECTED", "REJECTED");
        String alphaId = results.get(0).get("templateId").asText();
        assertThat(results.get(2).get("templateId").asText()).as("the copy points at the original").isEqualTo(alphaId);
        assertThat(results.get(3).get("templateId").asText()).as("the resized copy points at the original").isEqualTo(alphaId);
        assertThat(results.get(4).get("reason").asText()).containsIgnoringCase("image");
        assertThat(results.get(5).get("reason").asText()).containsIgnoringCase("small");

        // Where it came from and how to recognise it again are recorded.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_type, content_sha256, phash, collected_at FROM meme_template WHERE id = ?::uuid", alphaId);
        assertThat(row.get("source_type")).isEqualTo("UPLOAD");
        assertThat(row.get("content_sha256")).asString().hasSize(64);
        assertThat(row.get("phash")).isNotNull();
        assertThat(row.get("collected_at")).isNotNull();

        // The vision model (a mock here) looks at both pictures and publishes them.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            JsonNode stats = stats(admin);
            assertThat(stats.get("waitingForTags").asInt()).isZero();
            assertThat(stats.get("beingTagged").asInt()).isZero();
        });
        JsonNode published = template(admin, alphaId);
        assertThat(published.get("status").asText()).isEqualTo("APPROVED");
        assertThat(published.get("name").asText()).isEqualTo("alpha " + unique);
        assertThat(published.get("profile").get("meaning").asText()).isNotBlank();
        assertThat(published.get("profile").get("tags")).isNotEmpty();
        assertThat(published.get("slots")).as("a collected picture carries its own text").isEmpty();

        // And it can be found again by describing it.
        mvc.perform(post("/api/admin/index/sync").header("Authorization", bearer(admin))).andExpect(status().isOk());
        JsonNode hits = json.readTree(mvc.perform(get("/api/templates/search").param("q", "alpha " + unique)
                        .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        hits.forEach(h -> ids.add(h.get("templateId").asText()));
        assertThat(ids).contains(alphaId);
        JsonNode hit = null;
        for (JsonNode h : hits) {
            if (h.get("templateId").asText().equals(alphaId)) {
                hit = h;
            }
        }
        assertThat(hit.get("tags")).as("search hits carry their tags").isNotEmpty();
        assertThat(hit.get("meaning").asText()).isNotBlank();
        assertThat(hit.get("sourceType").asText()).isEqualTo("UPLOAD");
    }

    @Test
    void theCollectionCanOnlyBeManagedByAdministrators() throws Exception {
        String user = ordinaryUserToken();

        mvc.perform(get("/api/admin/collection/status").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/collection/files").file(file("a.png", png(picture(300, 300, false))))
                        .header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/collection/status")).andExpect(status().isUnauthorized());
    }

    // -- helpers ------------------------------------------------------------

    private static List<String> statuses(JsonNode results) {
        List<String> out = new ArrayList<>();
        results.forEach(r -> out.add(r.get("status").asText()));
        return out;
    }

    private JsonNode stats(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/collection/status").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode template(String token, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/templates/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String adminToken() throws Exception {
        var admin = securityProperties.bootstrapAdmin();
        return login(admin.username(), admin.password());
    }

    private String ordinaryUserToken() throws Exception {
        String username = "reader-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        users.save(User.register(username, hasher.hash(password), Role.USER));
        return login(username, password);
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

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, null, content);
    }

    /** A picture with a few large shapes, so its look survives resizing. */
    private static BufferedImage picture(int width, int height, boolean flipped) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(flipped
                ? new GradientPaint(width, 0, new Color(20, 30, 60), 0, 0, new Color(240, 220, 200))
                : new GradientPaint(0, 0, new Color(20, 30, 60), width, 0, new Color(240, 220, 200)));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(230, 60, 60));
        g.fillOval(width / 8, height / 6, width / 3, height / 2);
        g.setColor(new Color(40, 160, 90));
        g.fillRect(width / 2, height / 2, width / 3, height / 3);
        g.dispose();
        return image;
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
