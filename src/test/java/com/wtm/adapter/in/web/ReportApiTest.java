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
import com.wtm.application.port.out.ReviewPort;
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
import java.util.Random;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@AutoConfigureMockMvc
class ReportApiTest extends IntegrationTestBase {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewPort reviews;

    // -- what reaches the administrator ----------------------------------------------

    @Test
    void twoPeopleReportingGetTheModelsProposalInFrontOfTheAdministratorWhoCanAdoptIt() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        String alice = ordinaryUserToken();
        String bob = ordinaryUserToken();
        JsonNode before = template(admin, memeId);

        report(alice, memeId, "WRONG_TAGS", "標籤不準,這張其實是在講加班");
        report(alice, memeId, "WRONG_MEANING", "改成描述不貼切");   // the same person again: replaces the first
        report(bob, memeId, "OTHER", "");

        // Two newcomers are enough for the model to look, but not for anything to change by itself.
        JsonNode theCase = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> caseOf(admin, memeId), c -> c != null && "DONE".equals(c.path("review").path("status").asText()));
        assertThat(theCase.get("reports")).hasSize(2);
        assertThat(theCase.get("reports").findValuesAsText("reason")).containsExactlyInAnyOrder("WRONG_MEANING", "OTHER");
        assertThat(theCase.get("reports").findValuesAsText("comment")).contains("改成描述不貼切");
        assertThat(theCase.get("weight").asInt()).isEqualTo(2);
        assertThat(theCase.get("current").get("meaning").asText()).isEqualTo(before.get("profile").get("meaning").asText());
        JsonNode proposal = theCase.get("review").get("suggestion");
        assertThat(proposal.get("meaning").asText()).startsWith("模擬重新分析");
        assertThat(proposal.get("reasoning").asText()).contains("改成描述不貼切");   // the model was told
        assertThat(template(admin, memeId).get("profile")).isEqualTo(before.get("profile"));

        // Adopting it rewrites the description, keeps the meme published and closes the reports.
        mvc.perform(post("/api/admin/reports/" + memeId + "/apply").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        JsonNode after = template(admin, memeId);
        assertThat(after.get("profile").get("meaning").asText()).isEqualTo(proposal.get("meaning").asText());
        assertThat(after.get("status").asText()).isEqualTo("APPROVED");
        assertThat(caseOf(admin, memeId)).isNull();

        // Nothing is left to adopt. And a new report does not buy another look until the cooldown is over.
        mvc.perform(post("/api/admin/reports/" + memeId + "/apply").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        report(alice, memeId, "WRONG_TAGS", "又不準了");
        report(bob, memeId, "WRONG_TAGS", "我也覺得");
        JsonNode again = caseOf(admin, memeId);
        assertThat(again).isNotNull();
        assertThat(again.get("review").isNull()).as("the model is not asked again so soon").isTrue();
    }

    @Test
    void aSingleNewcomersReportIsKeptForTheAdministratorWithoutSpendingModelTime() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        report(ordinaryUserToken(), memeId, "WRONG_TAGS", "x");

        JsonNode theCase = caseOf(admin, memeId);
        assertThat(theCase.get("weight").asInt()).isEqualTo(1);
        assertThat(theCase.get("neededWeight").asInt()).isEqualTo(2);
        Thread.sleep(1_000);   // long enough for a worker to have picked something up, had anything been queued
        assertThat(caseOf(admin, memeId).get("review").isNull()).isTrue();

        // The administrator can still ask for the model's look by hand; that never waits on any budget.
        mvc.perform(post("/api/admin/reports/" + memeId + "/reanalyze").header("Authorization", bearer(admin)))
                .andExpect(status().isAccepted());
        await().atMost(PATIENCE).until(() -> "DONE".equals(caseOf(admin, memeId).path("review").path("status").asText()));
        mvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin))).andExpect(status().isOk());
        assertThat(caseOf(admin, memeId)).as("what an administrator asked for is not acted on by the rules").isNotNull();
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

    // -- the rules act on their own, and can be taken back ---------------------------------

    @Test
    void whenThreePeopleAgreeTheProposalIsAdoptedByItselfAndAdministratorsCanTakeItBack() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        JsonNode before = template(admin, memeId);

        for (int i = 0; i < 3; i++) {
            report(ordinaryUserToken(), memeId, "WRONG_TAGS", "標籤不對 " + i);
        }

        JsonNode entry = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> automaticEntry(admin, memeId), e -> e != null);
        assertThat(entry.get("action").asText()).isEqualTo("APPLIED");
        assertThat(entry.get("reports").asInt()).isEqualTo(3);
        assertThat(entry.get("canUndo").asBoolean()).isTrue();
        assertThat(entry.get("note").asText()).containsPattern("依據 [23] 則回報");   // as many as had reported when the model looked
        assertThat(caseOf(admin, memeId)).as("nothing is left for the administrator").isNull();
        assertThat(template(admin, memeId).get("profile").get("meaning").asText()).startsWith("模擬重新分析");

        mvc.perform(post("/api/admin/reports/" + memeId + "/undo").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        assertThat(template(admin, memeId).get("profile")).isEqualTo(before.get("profile"));
        assertThat(automaticEntry(admin, memeId).get("canUndo").asBoolean()).isFalse();
        mvc.perform(post("/api/admin/reports/" + memeId + "/undo").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void whenTheModelFindsNothingWrongTheReportsAreClosedAndCanBeOpenedAgain() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        JsonNode before = template(admin, memeId);

        report(ordinaryUserToken(), memeId, "WRONG_TAGS", "[keep] 其實沒什麼問題");
        report(ordinaryUserToken(), memeId, "WRONG_MEANING", "也許吧");

        JsonNode entry = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> automaticEntry(admin, memeId), e -> e != null);
        assertThat(entry.get("action").asText()).isEqualTo("DISMISSED");
        assertThat(caseOf(admin, memeId)).isNull();
        assertThat(template(admin, memeId).get("profile")).isEqualTo(before.get("profile"));

        // Taking it back opens the reports again, for the model to look once more and for the administrator to decide.
        mvc.perform(post("/api/admin/reports/" + memeId + "/undo").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        JsonNode reopened = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> caseOf(admin, memeId), c -> c != null && "DONE".equals(c.path("review").path("status").asText()));
        assertThat(reopened.get("reports")).hasSize(2);
        Thread.sleep(500);
        assertThat(caseOf(admin, memeId)).as("a look the administrator asked for is not acted on by the rules").isNotNull();
    }

    @Test
    void anythingAboutWhetherAPictureBelongsIsAlwaysLeftToTheAdministrator() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        for (int i = 0; i < 3; i++) {
            report(ordinaryUserToken(), memeId, "INAPPROPRIATE", "不該在這裡");
        }

        JsonNode theCase = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> caseOf(admin, memeId), c -> c != null && "DONE".equals(c.path("review").path("status").asText()));
        assertThat(theCase.get("reports")).hasSize(3);
        assertThat(automaticEntry(admin, memeId)).isNull();
    }

    @Test
    void sayingAPictureIsNotAMemeReachesTheAdministratorWhoDecidesWhateverTheModelThinks() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        // The mock model finds nothing wrong when a complaint says "[keep]"; for a question of whether the picture
        // belongs, that must still not close the reports by itself.
        report(ordinaryUserToken(), memeId, "NOT_A_MEME", "[keep] 這只是一張照片");
        report(ordinaryUserToken(), memeId, "NOT_A_MEME", "不是梗圖");

        JsonNode theCase = await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                .until(() -> caseOf(admin, memeId), c -> c != null && "DONE".equals(c.path("review").path("status").asText()));
        assertThat(theCase.get("reports").findValuesAsText("reason")).containsOnly("NOT_A_MEME");
        Thread.sleep(500);
        assertThat(caseOf(admin, memeId)).isNotNull();
        assertThat(automaticEntry(admin, memeId)).isNull();
    }

    // -- limits ----------------------------------------------------------------------------

    @Test
    void aPersonCanOnlyReportSoManyMemesADay() throws Exception {
        String admin = adminToken();
        List<String> memes = publishedMemes(admin, 11);
        String user = ordinaryUserToken();

        for (int i = 0; i < 10; i++) {
            report(user, memes.get(i), "OTHER", "x");
        }

        send(user, Map.of("templateId", memes.get(10), "reason", "OTHER", "comment", "x"))
                .andExpect(status().isTooManyRequests());
        // Changing what one already said about a meme is not a new meme.
        report(user, memes.get(0), "WRONG_TAGS", "改一下說法");
    }

    @Test
    void whenTheDaysBudgetIsUsedUpReportsWaitInLineExceptThoseAnAdministratorAskedFor() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);
        String otherId = publishedMeme(admin);   // the used-up budget is written against this one
        int dailyBudget = 50;
        // Use up what is left of today's budget with entries that are removed again at the end.
        Integer used = jdbc.queryForObject("SELECT count(*) FROM review_run WHERE ran_at > now() - interval '24 hours'",
                Integer.class);
        List<Long> filler = new ArrayList<>();
        for (int i = used; i < dailyBudget; i++) {
            filler.add(jdbc.queryForObject("INSERT INTO review_run (template_id) VALUES (?::uuid) RETURNING id",
                    Long.class, otherId));
        }
        try {
            report(ordinaryUserToken(), memeId, "WRONG_TAGS", "x");
            report(ordinaryUserToken(), memeId, "WRONG_TAGS", "y");
            Thread.sleep(1_500);
            assertThat(caseOf(admin, memeId).path("review").path("status").asText())
                    .as("queued but never started").isEqualTo("PENDING");
            assertThat(reviews.claim(1, dailyBudget)).isEmpty();

            // An administrator asking is not held back by the budget.
            mvc.perform(post("/api/admin/reports/" + memeId + "/reanalyze").header("Authorization", bearer(admin)))
                    .andExpect(status().isAccepted());
            await().atMost(PATIENCE).until(() -> "DONE".equals(caseOf(admin, memeId).path("review").path("status").asText()));
        } finally {
            for (Long id : filler) {
                jdbc.update("DELETE FROM review_run WHERE id = ?", id);
            }
        }
    }

    // -- access ------------------------------------------------------------------------------

    @Test
    void theModelCanBeAskedToLookAgainOnlyWhileThereAreReports() throws Exception {
        String admin = adminToken();
        String memeId = publishedMeme(admin);

        mvc.perform(post("/api/admin/reports/" + memeId + "/reanalyze").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
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
        mvc.perform(get("/api/admin/reports/automatic").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reports/" + id + "/apply").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reports/" + id + "/undo").header("Authorization", bearer(user)))
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
        return find(admin, "/api/admin/reports", memeId);
    }

    /** What the rules did on their own to one meme, or null when they did nothing. */
    private JsonNode automaticEntry(String admin, String memeId) throws Exception {
        return find(admin, "/api/admin/reports/automatic", memeId);
    }

    private JsonNode find(String admin, String path, String memeId) throws Exception {
        JsonNode all = json.readTree(mvc.perform(get(path).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode c : all) {
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
        return publishedMemes(admin, 1).get(0);
    }

    /** Collects new pictures and waits until the (mock) vision model has published them all. */
    private List<String> publishedMemes(String admin, int count) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartHttpServletRequestBuilder upload = multipart("/api/admin/collection/files");
        for (int i = 0; i < count; i++) {
            upload.file(new MockMultipartFile("files", "report-" + unique + "-" + i + ".png", null, png(picture())));
        }
        JsonNode results = json.readTree(mvc.perform(upload.header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        for (JsonNode result : results) {
            assertThat(result.get("status").asText()).isEqualTo("IMPORTED");
            ids.add(result.get("templateId").asText());
        }
        for (String id : ids) {
            await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> assertThat(template(admin, id).get("status").asText()).isEqualTo("APPROVED"));
        }
        return ids;
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
