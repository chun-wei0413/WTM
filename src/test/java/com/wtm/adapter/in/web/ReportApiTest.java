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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
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
class ReportApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;

    @Test
    void aReportReachesTheAdministratorWithTheModelsNewProposalAndCanBeAdopted() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        String alice = ordinaryUserToken();
        String bob = ordinaryUserToken();
        JsonNode before = template(admin, memeId);

        report(alice, memeId, "WRONG_TAGS", "標籤不準,這張其實是在講加班");
        report(alice, memeId, "WRONG_MEANING", "改成描述不貼切");   // the same person again: replaces the first
        report(bob, memeId, "OTHER", "");

        // The administrator sees one case with the complaints, and the model's proposal arrives.
        JsonNode theCase = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> caseOf(admin, memeId), c -> c != null && "DONE".equals(c.path("review").path("status").asText()));
        assertThat(theCase.get("reports")).hasSize(2);
        assertThat(theCase.get("reports").findValuesAsText("reason")).containsExactlyInAnyOrder("WRONG_MEANING", "OTHER");
        assertThat(theCase.get("reports").findValuesAsText("comment")).contains("改成描述不貼切");
        assertThat(theCase.get("current").get("meaning").asText()).isEqualTo(before.get("profile").get("meaning").asText());
        JsonNode proposal = theCase.get("review").get("suggestion");
        assertThat(proposal.get("meaning").asText()).startsWith("模擬重新分析");
        assertThat(proposal.get("reasoning").asText()).contains("改成描述不貼切");   // the model was told

        // Adopting it rewrites the description, keeps the meme published and closes the reports.
        mvc.perform(post("/api/admin/reports/" + memeId + "/apply").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        JsonNode after = template(admin, memeId);
        assertThat(after.get("profile").get("meaning").asText()).isEqualTo(proposal.get("meaning").asText());
        assertThat(after.get("status").asText()).isEqualTo("APPROVED");
        assertThat(caseOf(admin, memeId)).isNull();

        // Nothing is left to adopt, and it may be reported again afterwards.
        mvc.perform(post("/api/admin/reports/" + memeId + "/apply").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        report(alice, memeId, "WRONG_TAGS", "又不準了");
        assertThat(caseOf(admin, memeId)).isNotNull();
    }

    @Test
    void dismissingClosesTheReportsWithoutChangingTheMeme() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        JsonNode before = template(admin, memeId);
        report(ordinaryUserToken(), memeId, "WRONG_TAGS", "x");

        mvc.perform(post("/api/admin/reports/" + memeId + "/dismiss").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        assertThat(caseOf(admin, memeId)).isNull();
        assertThat(template(admin, memeId).get("profile")).isEqualTo(before.get("profile"));
    }

    @Test
    void theModelCanBeAskedToLookAgainOnlyWhileThereAreReports() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        mvc.perform(post("/api/admin/reports/" + memeId + "/reanalyze").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        report(ordinaryUserToken(), memeId, "WRONG_TAGS", "x");
        await().atMost(Duration.ofSeconds(30)).until(() -> "DONE".equals(
                caseOf(admin, memeId).path("review").path("status").asText()));
        mvc.perform(post("/api/admin/reports/" + memeId + "/reanalyze").header("Authorization", bearer(admin)))
                .andExpect(status().isAccepted());
    }

    @Test
    void reportsAreRefusedForUnknownMemesAndBadInput() throws Exception {
        String user = ordinaryUserToken();
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        send(user, Map.of("templateId", UUID.randomUUID(), "reason", "WRONG_TAGS", "comment", "x"))
                .andExpect(status().isNotFound());
        send(user, Map.of("templateId", memeId, "reason", "NOT_A_REASON")).andExpect(status().isBadRequest());
        send(user, Map.of("reason", "OTHER")).andExpect(status().isBadRequest());
        send(user, Map.of("templateId", memeId, "reason", "OTHER", "comment", "字".repeat(400)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdministratorsSeeTheReports() throws Exception {
        String user = ordinaryUserToken();
        String id = UUID.randomUUID().toString();

        mvc.perform(get("/api/admin/reports").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reports/" + id + "/apply").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // -- helpers ------------------------------------------------------------

    private void report(String token, String memeId, String reason, String comment) throws Exception {
        send(token, Map.of("templateId", memeId, "reason", reason, "comment", comment)).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearer(token)).content(json.writeValueAsString(body)));
    }

    /** The administrator's case for one meme, or null when it has no open reports. */
    private JsonNode caseOf(String admin, String memeId) throws Exception {
        JsonNode cases = json.readTree(mvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode c : cases) {
            if (c.get("templateId").asText().equals(memeId)) {
                return c;
            }
        }
        return null;
    }

    private JsonNode template(String admin, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/templates/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String publishedMeme(String admin) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile file = new MockMultipartFile("files", "report-" + unique + ".png", null, png(picture()));
        JsonNode result = json.readTree(mvc.perform(multipart("/api/admin/collection/files").file(file)
                        .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).get(0);
        assertThat(result.get("status").asText()).isEqualTo("IMPORTED");
        String id = result.get("templateId").asText();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(template(admin, id).get("status").asText()).isEqualTo("APPROVED"));
        return id;
    }

    private String adminToken() throws Exception {
        var admin = securityProperties.bootstrapAdmin();
        return login(admin.username(), admin.password());
    }

    private String ordinaryUserToken() throws Exception {
        String username = "reporter-" + UUID.randomUUID();
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
    private static BufferedImage picture() {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        var random = new Random();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                g.fillRect(col * 80, row * 60, 81, 61);
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
