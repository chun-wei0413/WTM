package com.usethatmeme.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.IntegrationTestBase;
import com.usethatmeme.adapter.security.SecurityProperties;
import com.usethatmeme.application.port.out.MemeSourcePort;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A whole collection run against a small website on this machine: real downloads, real
 * deduplication, real run records. The collector's private-address protection is switched off
 * for this class only, because the test site is on this machine.
 */
@AutoConfigureMockMvc
@Import(CollectionRunApiTest.TestSourceConfig.class)
@TestPropertySource(properties = {
        "usethatmeme.sources.allow-private-addresses=true",
        "usethatmeme.sources.min-delay=PT0.01S"})
class CollectionRunApiTest extends IntegrationTestBase {

    private static final HttpServer SITE = startSite();
    private static final String SITE_URL = "http://127.0.0.1:" + SITE.getAddress().getPort();

    /** Which batch of pictures the test source offers. Each test picks its own, so none sees another's. */
    private static volatile String namespace = "default";

    private static HttpServer startSite() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String[] parts = exchange.getRequestURI().getPath().split("/");   // "", namespace, file
                String file = parts.length > 2 ? parts[2] : "";
                byte[] body;
                String type;
                int status = 200;
                switch (file) {
                    case "one.png", "one-copy.png" -> {
                        body = picture(parts[1] + "-one");      // the copy has exactly the same bytes
                        type = "image/png";
                    }
                    case "two.png" -> {
                        body = picture(parts[1] + "-two");
                        type = "image/png";
                    }
                    case "page.html" -> {
                        body = "<html>not a picture</html>".getBytes();
                        type = "text/html";
                    }
                    default -> {
                        body = "missing".getBytes();
                        type = "text/plain";
                        status = 404;
                    }
                }
                exchange.getResponseHeaders().add("Content-Type", type);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopSite() {
        SITE.stop(0);
    }

    /** A source that offers pictures from the test site. */
    @TestConfiguration
    static class TestSourceConfig {
        @Bean
        MemeSourcePort testSource() {
            return new MemeSourcePort() {
                @Override
                public String id() {
                    return "TESTSRC";
                }

                @Override
                public String displayName() {
                    return "Test site";
                }

                @Override
                public String description() {
                    return "A site on this machine";
                }

                @Override
                public List<SourceOption> options() {
                    return List.of();
                }

                @Override
                public Iterator<RemoteMeme> discover(Map<String, String> options) {
                    String base = SITE_URL + "/" + namespace;
                    return List.of(
                            new RemoteMeme(base + "/one.png", base + "/p1", "First meme", "Tester", "CC0"),
                            new RemoteMeme(base + "/two.png", base + "/p2", "Second meme", null, null),
                            new RemoteMeme(base + "/one-copy.png", base + "/p3", "First meme again", null, null),
                            new RemoteMeme(base + "/page.html", null, "Not a picture", null, null),
                            new RemoteMeme(base + "/missing.png", null, "Gone", null, null)).iterator();
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SecurityProperties securityProperties;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void aRunDownloadsDeduplicatesAndRecordsEverything() throws Exception {
        String admin = adminToken();
        waitUntilNothingIsRunning(admin);
        namespace = "run-" + UUID.randomUUID();
        String base = SITE_URL + "/" + namespace;

        MvcResult started = mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"testsrc\", \"limit\": 10}"))
                .andExpect(status().isAccepted()).andReturn();
        String runId = json.readTree(started.getResponse().getContentAsString()).get("runId").asText();

        JsonNode run = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getRun(admin, runId), r -> !r.get("status").asText().equals("RUNNING"));

        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.get("counts").get("found").asInt()).isEqualTo(5);
        assertThat(run.get("counts").get("imported").asInt()).isEqualTo(2);
        assertThat(run.get("counts").get("duplicates").asInt()).isEqualTo(1);
        assertThat(run.get("counts").get("rejected").asInt()).isEqualTo(2);
        assertThat(run.get("counts").get("failed").asInt()).isZero();
        assertThat(run.get("message").asText()).contains("Looked at 5", "2 added");

        // What was collected remembers where it came from.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meme_template WHERE source_url LIKE ?", Long.class,
                base + "/%")).isEqualTo(2L);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_url, source_page_url, attribution, license_note FROM meme_template WHERE source_url = ?",
                base + "/one.png");
        assertThat(row.get("source_page_url")).isEqualTo(base + "/p1");
        assertThat(row.get("attribution")).isEqualTo("Tester");
        assertThat(row.get("license_note")).isEqualTo("CC0");

        // The same source again finds nothing new.
        MvcResult again = mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"TESTSRC\", \"limit\": 10}"))
                .andExpect(status().isAccepted()).andReturn();
        String secondId = json.readTree(again.getResponse().getContentAsString()).get("runId").asText();
        JsonNode second = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getRun(admin, secondId), r -> !r.get("status").asText().equals("RUNNING"));
        assertThat(second.get("counts").get("imported").asInt()).isZero();
        assertThat(second.get("counts").get("duplicates").asInt()).isEqualTo(3);
    }

    @Test
    void theLimitStopsARunEarly() throws Exception {
        String admin = adminToken();
        waitUntilNothingIsRunning(admin);
        namespace = "limit-" + UUID.randomUUID();

        MvcResult started = mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"TESTSRC\", \"limit\": 2}"))
                .andExpect(status().isAccepted()).andReturn();
        String runId = json.readTree(started.getResponse().getContentAsString()).get("runId").asText();

        JsonNode run = await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> getRun(admin, runId), r -> !r.get("status").asText().equals("RUNNING"));

        assertThat(run.get("counts").get("found").asInt()).isEqualTo(2);
    }

    @Test
    void listsTheSourcesAndRecentRuns() throws Exception {
        String admin = adminToken();

        JsonNode sources = json.readTree(mvc.perform(get("/api/admin/collection/sources")
                        .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        List<String> ids = new java.util.ArrayList<>();
        sources.forEach(s -> ids.add(s.get("id").asText()));
        assertThat(ids).contains("IMGFLIP", "WIKIMEDIA", "PTT", "TESTSRC");
        assertThat(sources.toString()).doesNotContain("DCARD");

        mvc.perform(get("/api/admin/collection/runs").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    @Test
    void addsASinglePictureFromItsAddress() throws Exception {
        String admin = adminToken();
        String address = SITE_URL + "/single-" + UUID.randomUUID() + "/two.png";

        JsonNode first = postUrl(admin, Map.of("url", address, "title", "By hand"));
        assertThat(first.get("status").asText()).isEqualTo("IMPORTED");
        assertThat(jdbc.queryForObject("SELECT source_type FROM meme_template WHERE id = ?::uuid", String.class,
                first.get("templateId").asText())).isEqualTo("URL");

        // The same address again finds the picture already there.
        assertThat(postUrl(admin, Map.of("url", address)).get("status").asText()).isEqualTo("DUPLICATE");

        // Something that is not a picture, and an address that must never be read.
        mvc.perform(post("/api/admin/collection/url").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", SITE_URL + "/x/page.html"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/collection/url").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "file:///etc/passwd"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/collection/url").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badRequestsAndOrdinaryUsersAreRefused() throws Exception {
        String admin = adminToken();
        waitUntilNothingIsRunning(admin);

        mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"DCARD\", \"limit\": 10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"TESTSRC\", \"limit\": 0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/collection/runs/" + UUID.randomUUID()).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        String user = ordinaryUserToken();
        mvc.perform(post("/api/admin/collection/runs").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"TESTSRC\", \"limit\": 1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/collection/sources").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    // -- helpers ------------------------------------------------------------

    private void waitUntilNothingIsRunning(String admin) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            JsonNode runs = json.readTree(mvc.perform(get("/api/admin/collection/runs")
                    .header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString());
            for (JsonNode r : runs) {
                if (r.get("status").asText().equals("RUNNING")) {
                    return false;
                }
            }
            return true;
        });
    }

    private JsonNode postUrl(String token, Map<String, String> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/collection/url").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getRun(String token, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/collection/runs/" + id).header("Authorization", bearer(token)))
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

    /**
     * A picture of 9x8 random grey blocks. The same seed always gives the same bytes; a different seed
     * gives a picture whose look is unrelated, so the library never takes it for a copy.
     */
    private static byte[] picture(String seed) {
        try {
            java.util.Random random = new java.util.Random(seed.hashCode());
            BufferedImage image = new BufferedImage(540, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 9; column++) {
                    int grey = random.nextInt(256);
                    g.setColor(new Color(grey, grey, grey));
                    g.fillRect(column * 60, row * 50, 60, 50);
                }
            }
            g.dispose();
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
