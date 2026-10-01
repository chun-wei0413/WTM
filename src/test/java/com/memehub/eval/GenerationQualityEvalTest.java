package com.memehub.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import com.memehub.application.generation.GenerationQueueHandler;
import com.memehub.application.generation.GenerationView;
import com.memehub.application.generation.GetGenerationHandler;
import com.memehub.application.generation.RunGenerationHandler;
import com.memehub.application.generation.SubmitGenerationHandler;
import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.UserRepository;
import com.memehub.application.template.command.ApproveTemplateHandler;
import com.memehub.application.template.command.DefineSlotHandler;
import com.memehub.application.template.command.DraftTemplateHandler;
import com.memehub.application.template.command.ReviseProfileHandler;
import com.memehub.application.template.index.SyncSearchIndexHandler;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.Slot;
import com.memehub.domain.template.TemplateId;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the whole generation pipeline with a real model and writes the results
 * to {@code target/eval-memes/} so the captions and the layout can be judged by eye.
 *
 * <p>Not part of the normal build. Requires Ollama with {@code bge-m3} and {@code qwen2.5:7b}:
 * <pre>mvn test -Dtest=GenerationQualityEvalTest -Dmemehub.eval=true</pre>
 */
@EnabledIfSystemProperty(named = "memehub.eval", matches = "true")
@TestPropertySource(properties = "memehub.generation.worker-enabled=false")
class GenerationQualityEvalTest extends IntegrationTestBase {

    private static final List<String> SITUATIONS = List.of(
            "老闆臨時又改需求,我還是假裝沒事繼續工作",
            "手上專案還沒做完,又看到新技術想去玩",
            "今天終於準時下班了",
            "要存錢還是買新耳機,好糾結",
            "我把客人的抱怨當成讚美回覆了");

    @DynamicPropertySource
    static void realModels(DynamicPropertyRegistry registry) {
        registry.add("memehub.embedding.provider", () -> "ollama");
        registry.add("memehub.llm.provider", () -> "ollama");
    }

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired SyncSearchIndexHandler sync;
    @Autowired DraftTemplateHandler draft;
    @Autowired ReviseProfileHandler reviseProfile;
    @Autowired DefineSlotHandler defineSlot;
    @Autowired ApproveTemplateHandler approve;
    @Autowired SubmitGenerationHandler submit;
    @Autowired GenerationQueueHandler queue;
    @Autowired RunGenerationHandler runner;
    @Autowired GetGenerationHandler get;

    @Test
    void generateMemesWithRealModels() throws Exception {
        jdbc.update("DELETE FROM template_search");
        jdbc.update("DELETE FROM generation_job");
        jdbc.update("DELETE FROM meme");
        jdbc.update("DELETE FROM meme_template");

        List<SearchQualityEvalTest.TemplateSeed> seeds = read("eval/templates.json", new TypeReference<>() { });
        for (var seed : seeds) {
            TemplateId id = draft.handle(seed.name(), blankTemplate(seed));
            reviseProfile.handle(id, new MemeProfile(seed.meaning(), seed.usageExamples(), seed.emotions(), seed.aliases()));
            int slotNo = 1;
            for (var s : seed.slots()) {
                defineSlot.handle(id, new Slot(slotNo++, s.role(), s.maxChars(), true, s.x(), s.y(), s.width(), s.height()));
            }
            approve.handle(id);
        }
        assertThat(sync.handle(100).failed()).isZero();

        User user = User.register("eval-" + UUID.randomUUID(), hasher.hash(UUID.randomUUID().toString()), Role.USER);
        users.save(user);

        Path outDir = Path.of("target", "eval-memes");
        Files.createDirectories(outDir);
        StringBuilder report = new StringBuilder();
        int number = 0;
        for (String situation : SITUATIONS) {
            number++;
            long started = System.nanoTime();
            UUID jobId = submit.handle(user.id(), situation);
            queue.claim(10).forEach(runner::handle);
            double seconds = (System.nanoTime() - started) / 1e9;

            GenerationView view = get.handle(user.id(), jobId);
            report.append(String.format(Locale.ROOT, "#%d  %s%n    status: %s   %.1fs%s%n", number, situation,
                    view.status(), seconds, view.failureReason() == null ? "" : "   (" + view.failureReason() + ")"));
            int candidate = 0;
            for (var c : view.candidates()) {
                candidate++;
                report.append("    ").append(candidate).append(". ").append(c.templateName()).append("  ").append(c.captions()).append('\n');
                Files.write(outDir.resolve(number + "-" + candidate + ".png"), download(c.imageUrl()));
            }
            report.append('\n');
        }

        System.out.println(report);
        Files.writeString(outDir.resolve("report.txt"), report.toString());
    }

    // -- helpers ------------------------------------------------------------

    private <T> T read(String resource, TypeReference<T> type) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return json.readValue(in, type);
        }
    }

    /** A neutral background with a thin outline per slot, so the layout can be judged. */
    private static byte[] blankTemplate(SearchQualityEvalTest.TemplateSeed seed) throws Exception {
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x7A8CA5));
        g.fillRect(0, 0, 600, 600);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 6f}, 0f));
        for (var s : seed.slots()) {
            g.drawRect(s.x(), s.y(), s.width(), s.height());
        }
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] download(String url) throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
