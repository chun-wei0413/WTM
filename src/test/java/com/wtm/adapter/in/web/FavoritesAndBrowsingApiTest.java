package com.wtm.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.awt.Graphics2D;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class FavoritesAndBrowsingApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;

    @Test
    void aUserKeepsTheirOwnFavoritesAndCanDownloadThePicture() throws Exception {
        String memeId = publishedMeme();
        String alice = ordinaryUserToken();
        String bob = ordinaryUserToken();

        mvc.perform(put("/api/favorites/" + memeId).header("Authorization", bearer(alice))).andExpect(status().isNoContent());
        mvc.perform(put("/api/favorites/" + memeId).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent()); // adding it again changes nothing

        assertThat(favoriteIds(alice)).containsExactly(memeId);
        assertThat(favoriteIds(bob)).as("favorites are private to their owner").doesNotContain(memeId);
        JsonNode item = json.readTree(mvc.perform(get("/api/favorites").header("Authorization", bearer(alice)))
                .andReturn().getResponse().getContentAsString()).get(0);
        assertThat(item.get("imageUrl").asText()).startsWith("http");
        assertThat(item.get("meaning").asText()).isNotBlank();

        mvc.perform(delete("/api/favorites/" + memeId).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());
        assertThat(favoriteIds(alice)).isEmpty();

        // The picture can be fetched through the application, as the original file.
        MvcResult image = mvc.perform(get("/api/library/" + memeId + "/image").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andReturn();
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getHeader("Content-Disposition")).contains("attachment");
        assertThat(image.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void onlyPublishedMemesCanBeFavoritedOrDownloaded() throws Exception {
        String user = ordinaryUserToken();
        String unknown = UUID.randomUUID().toString();

        mvc.perform(put("/api/favorites/" + unknown).header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/library/" + unknown + "/image").header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
        // Taking away something that was never a favorite is not an error.
        mvc.perform(delete("/api/favorites/" + unknown).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
    }

    @Test
    void randomMemesAreOfferedToSignedInUsersOnly() throws Exception {
        String memeId = publishedMeme();
        String user = ordinaryUserToken();

        JsonNode memes = json.readTree(mvc.perform(get("/api/library/random").param("limit", "50")
                        .header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        memes.forEach(m -> ids.add(m.get("templateId").asText()));
        assertThat(ids).contains(memeId);

        mvc.perform(get("/api/library/random")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/library/hot-searches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/library/random").param("limit", "0").header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void phrasesPeopleFoundSomethingWithBecomeHotSearches() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme();
        mvc.perform(post("/api/admin/index/sync").header("Authorization", bearer(admin))).andExpect(status().isOk());
        String name = json.readTree(mvc.perform(get("/api/admin/templates/" + memeId)
                .header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString())
                .get("name").asText();

        String first = ordinaryUserToken();
        String second = ordinaryUserToken();
        for (String token : List.of(first, second, second)) {
            mvc.perform(get("/api/templates/search").param("q", name.toUpperCase())
                    .header("Authorization", bearer(token))).andExpect(status().isOk());
        }
        // A search that finds nothing is not worth offering as a shortcut. A long sentence is not either.
        mvc.perform(get("/api/templates/search").param("q", "x".repeat(60)).header("Authorization", bearer(first)))
                .andExpect(status().isOk());

        JsonNode hot = json.readTree(mvc.perform(get("/api/library/hot-searches").header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode h : hot) {
            if (h.get("term").asText().equals(name.toLowerCase())) {
                mine = h;
            }
        }
        assertThat(mine).as("the phrase, counted without regard to capitals").isNotNull();
        assertThat(mine.get("searches").asInt()).isEqualTo(3);
        hot.forEach(h -> assertThat(h.get("term").asText()).hasSizeLessThanOrEqualTo(30));
    }

    // -- helpers ------------------------------------------------------------

    /** Collects one new picture and waits until the (mock) vision model has published it. */
    private String publishedMeme() throws Exception {
        String admin = adminToken();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile file = new MockMultipartFile("files", "browse-" + unique + ".png", null,
                png(picture(640, 480)));
        JsonNode result = json.readTree(mvc.perform(multipart("/api/admin/collection/files").file(file)
                        .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).get(0);
        assertThat(result.get("status").asText()).isEqualTo("IMPORTED");
        String id = result.get("templateId").asText();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            JsonNode template = json.readTree(mvc.perform(get("/api/admin/templates/" + id)
                    .header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString());
            assertThat(template.get("status").asText()).isEqualTo("APPROVED");
        });
        return id;
    }

    private List<String> favoriteIds(String token) throws Exception {
        JsonNode items = json.readTree(mvc.perform(get("/api/favorites").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        items.forEach(i -> ids.add(i.get("templateId").asText()));
        return ids;
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

    /** A grid of random blocks, so two pictures never look alike to the duplicate check. */
    private static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        var random = new java.util.Random();
        int cells = 8;
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                g.fillRect(col * width / cells, row * height / cells, width / cells + 1, height / cells + 1);
            }
        }
        g.dispose();
        return image;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
