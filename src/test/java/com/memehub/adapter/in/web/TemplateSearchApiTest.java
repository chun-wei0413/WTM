package com.memehub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import com.memehub.adapter.security.SecurityProperties;
import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
class TemplateSearchApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        var admin = securityProperties.bootstrapAdmin();
        adminToken = login(admin.username(), admin.password());
    }

    @Test
    void trigramMatchingWorksOnChineseText() {
        Double similarity = jdbc.queryForObject(
                "SELECT word_similarity('量子貓咪', '量子貓咪議會的決議')", Double.class);

        assertThat(similarity).isGreaterThan(0.5);
    }

    @Test
    void searchRequiresAuthenticationAndAText() throws Exception {
        mvc.perform(get("/api/templates/search").param("q", "anything")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/templates/search").param("q", "   ").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvedTemplateBecomesSearchableOnlyAfterTheIndexIsSynced() throws Exception {
        String marker = "marker" + UUID.randomUUID().toString().replace("-", "");
        String id = approvedTemplate("Drake " + marker, "拒絕一件事,偏好另一件事",
                List.of("不想寫測試,直接上線 " + marker, "拒絕加班,選擇下班"));
        approvedTemplate("Distracted Boyfriend", "對新事物心動而冷落舊的",
                List.of("看到新框架就想放棄舊專案 " + UUID.randomUUID()));

        assertThat(searchIds(marker)).doesNotContain(id);

        sync();

        assertThat(searchIds(marker)).startsWith(id);
        // A phrase from the usage examples finds it semantically, ahead of the unrelated template.
        List<String> semantic = searchIds("不想寫測試,直接上線");
        assertThat(semantic).contains(id);
        JsonNode first = search("不想寫測試,直接上線").get(0);
        assertThat(first.get("templateId").asText()).isEqualTo(id);
        assertThat(first.get("imageUrl").asText()).startsWith("http");
        assertThat(first.get("slots")).hasSize(1);
    }

    @Test
    void revisedTemplateIsReindexedAndRetiredTemplateDisappears() throws Exception {
        String oldMarker = "old" + UUID.randomUUID().toString().replace("-", "");
        String newMarker = "new" + UUID.randomUUID().toString().replace("-", "");
        String id = approvedTemplate("Reaction", "meaning", List.of("usage " + oldMarker));
        sync();
        assertThat(searchIds(oldMarker)).startsWith(id);

        put("/api/admin/templates/" + id + "/profile",
                profileJson("meaning", List.of("usage " + newMarker))).andExpect(status().isNoContent());
        sync();

        assertThat(searchIds(newMarker)).startsWith(id);
        // Vector search always returns the nearest templates, so check the stored text itself.
        String indexedText = jdbc.queryForObject(
                "SELECT search_text FROM template_search WHERE template_id = ?::uuid", String.class, id);
        assertThat(indexedText).contains(newMarker).doesNotContain(oldMarker);

        action(id, "retire").andExpect(status().isNoContent());
        var result = sync();

        assertThat(result.get("removed").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(searchIds(newMarker)).doesNotContain(id);
    }

    @Test
    void regularUsersCanSearchButCannotTriggerSync() throws Exception {
        String username = "searcher-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        users.save(User.register(username, hasher.hash(password), Role.USER));
        String userToken = login(username, password);

        mvc.perform(get("/api/templates/search").param("q", "anything")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/index/sync").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    // -- helpers ------------------------------------------------------------

    private String approvedTemplate(String name, String meaning, List<String> usage) throws Exception {
        var file = new MockMultipartFile("file", "t.png", "image/png", png());
        MvcResult created = mvc.perform(multipart("/api/admin/templates").file(file).param("name", name)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();

        put("/api/admin/templates/" + id + "/profile", profileJson(meaning, usage))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/admin/templates/" + id + "/slots").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotNo": 1, "role": "caption", "maxChars": 12, "required": true,
                                 "x": 0, "y": 0, "width": 100, "height": 50}"""))
                .andExpect(status().isNoContent());
        action(id, "approve").andExpect(status().isNoContent());
        return id;
    }

    private String profileJson(String meaning, List<String> usage) throws Exception {
        return json.writeValueAsString(Map.of("meaning", meaning, "usageExamples", usage,
                "emotions", List.of(), "aliases", List.of()));
    }

    private ResultActions put(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions action(String id, String action) throws Exception {
        return mvc.perform(post("/api/admin/templates/" + id + "/" + action)
                .header("Authorization", bearer(adminToken)));
    }

    private JsonNode sync() throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/index/sync").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode search(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/templates/search").param("q", query)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private List<String> searchIds(String query) throws Exception {
        List<String> ids = new ArrayList<>();
        search(query).forEach(n -> ids.add(n.get("templateId").asText()));
        return ids;
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
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
