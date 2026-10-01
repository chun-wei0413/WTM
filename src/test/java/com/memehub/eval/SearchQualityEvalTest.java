package com.memehub.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.IntegrationTestBase;
import com.memehub.application.port.out.EmbeddingPort;
import com.memehub.application.port.out.TemplateSearchPort;
import com.memehub.application.template.command.ApproveTemplateHandler;
import com.memehub.application.template.command.DefineSlotHandler;
import com.memehub.application.template.command.DraftTemplateHandler;
import com.memehub.application.template.command.ReviseProfileHandler;
import com.memehub.application.template.index.SyncSearchIndexHandler;
import com.memehub.application.template.search.SearchResult;
import com.memehub.application.template.search.SearchTemplatesHandler;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.Slot;
import com.memehub.domain.template.TemplateId;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Measures how well search finds the right template, using a real embedding model.
 *
 * <p>Not part of the normal build. Requires Ollama with {@code bge-m3} on localhost:11434:
 * <pre>mvn test -Dtest=SearchQualityEvalTest -Dmemehub.eval=true</pre>
 * The report is printed and written to {@code target/search-eval.txt}.
 *
 * <p>Besides the production search it also scores an alternative that embeds every
 * usage example on its own ("multi-vector"), computed in memory, to decide whether
 * that idea is worth putting into the index.
 */
@EnabledIfSystemProperty(named = "memehub.eval", matches = "true")
class SearchQualityEvalTest extends IntegrationTestBase {

    private static final int DEPTH = 12;

    @DynamicPropertySource
    static void realEmbeddings(DynamicPropertyRegistry registry) {
        registry.add("memehub.embedding.provider", () -> "ollama");
    }

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddingPort embeddings;
    @Autowired TemplateSearchPort searchPort;
    @Autowired SearchTemplatesHandler hybrid;
    @Autowired SyncSearchIndexHandler sync;
    @Autowired DraftTemplateHandler draft;
    @Autowired ReviseProfileHandler reviseProfile;
    @Autowired DefineSlotHandler defineSlot;
    @Autowired ApproveTemplateHandler approve;

    record TemplateSeed(String name, List<String> aliases, String meaning, List<String> usageExamples,
                        List<String> emotions, List<SlotSeed> slots) {
    }

    record SlotSeed(String role, int maxChars, int x, int y, int width, int height) {
    }

    record Query(String query, String expected) {
    }

    /** Embeddings of the separate pieces of one template, for the multi-vector prototype. */
    record Pieces(float[] meaning, List<float[]> examples) {
    }

    @Test
    void reportSearchQuality() throws Exception {
        jdbc.update("DELETE FROM template_search");
        jdbc.update("DELETE FROM meme_template");

        List<TemplateSeed> seeds = read("eval/templates.json", new TypeReference<>() { });
        Map<UUID, String> names = seedTemplates(seeds);
        var synced = sync.handle(100);
        assertThat(synced.indexed()).isEqualTo(names.size());
        assertThat(synced.failed()).isZero();

        Map<String, Pieces> pieces = embedPieces(seeds);
        List<Query> situations = read("eval/queries.json", new TypeReference<>() { });
        List<Query> nameQueries = read("eval/name-queries.json", new TypeReference<>() { });

        StringBuilder report = new StringBuilder();
        report.append("Real embedding model: bge-m3 via Ollama, ").append(names.size()).append(" templates\n");

        // 1. Production search, situation queries ------------------------------------
        report.append("\n== Situation queries (").append(situations.size()).append(") ==\n");
        report.append(String.format(Locale.ROOT, "%-34s %-16s %5s %5s %5s  %s%n",
                "query", "expected", "vec", "kw", "hyb", "nearest (cosine distance)"));
        Stats vec = new Stats(), kw = new Stats(), hyb = new Stats();
        Stats blob = new Stats(), examplesOnly = new Stats(), maxAll = new Stats(), avgMix = new Stats();
        List<Double> onTopic = new ArrayList<>(), offTopic = new ArrayList<>();

        for (Query q : situations) {
            float[] qv = embeddings.embed(q.query());
            List<String> vector = searchPort.byVector(qv, DEPTH).stream().map(names::get).toList();
            List<String> keyword = searchPort.byKeyword(q.query(), DEPTH).stream().map(names::get).toList();
            List<String> fused = hybrid.handle(q.query(), DEPTH).stream().map(SearchResult::name).toList();
            double nearest = nearestDistance(qv);
            String top = vector.isEmpty() ? "-" : vector.get(0);

            if (q.expected() == null) {
                offTopic.add(nearest);
                report.append(String.format(Locale.ROOT, "%-34s %-16s %5s %5s %5s  %s (%.3f)%n",
                        clip(q.query(), 30), "(off-topic)", "-", "-", "-", top, nearest));
                continue;
            }
            onTopic.add(nearest);
            report.append(String.format(Locale.ROOT, "%-34s %-16s %5s %5s %5s  %s (%.3f)%n",
                    clip(q.query(), 30), clip(q.expected(), 14), rank(vec.record(vector, q.expected())),
                    rank(kw.record(keyword, q.expected())), rank(hyb.record(fused, q.expected())), top, nearest));

            // Multi-vector prototype, same query vector.
            Map<String, Double> blobSim = blobSimilarities(qv, names);
            blob.record(rankBy(blobSim), q.expected());
            Map<String, Double> exampleSim = new HashMap<>(), all = new HashMap<>(), mix = new HashMap<>();
            for (var e : pieces.entrySet()) {
                double bestExample = e.getValue().examples().stream().mapToDouble(x -> cosine(qv, x)).max().orElse(-1);
                double meaning = cosine(qv, e.getValue().meaning());
                double b = blobSim.get(e.getKey());
                exampleSim.put(e.getKey(), bestExample);
                all.put(e.getKey(), Math.max(b, Math.max(bestExample, meaning)));
                mix.put(e.getKey(), (b + bestExample) / 2);
            }
            examplesOnly.record(rankBy(exampleSim), q.expected());
            maxAll.record(rankBy(all), q.expected());
            avgMix.record(rankBy(mix), q.expected());
        }

        report.append("\n").append(String.format(Locale.ROOT, "%-34s %8s %8s %8s%n", "production search", "hit@1", "hit@3", "MRR"));
        report.append(vec.line("vector")).append(kw.line("keyword")).append(hyb.line("hybrid"));
        report.append("\n").append(String.format(Locale.ROOT, "%-34s %8s %8s %8s%n", "vector variants (in memory)", "hit@1", "hit@3", "MRR"));
        report.append(blob.line("one vector per template (now)"))
                .append(examplesOnly.line("best single usage example"))
                .append(maxAll.line("best of blob/example/meaning"))
                .append(avgMix.line("avg of blob and best example"));
        report.append("\nNearest cosine distance, on-topic:  ").append(summary(onTopic));
        report.append("\nNearest cosine distance, off-topic: ").append(summary(offTopic)).append('\n');

        // 2. Name queries --------------------------------------------------------------
        report.append("\n== Name queries (").append(nameQueries.size()).append(") ==\n");
        report.append(String.format(Locale.ROOT, "%-24s %-16s %5s %5s %5s%n", "query", "expected", "vec", "kw", "hyb"));
        Stats nVec = new Stats(), nKw = new Stats(), nHyb = new Stats();
        for (Query q : nameQueries) {
            List<String> vector = searchPort.byVector(embeddings.embed(q.query()), DEPTH).stream().map(names::get).toList();
            List<String> keyword = searchPort.byKeyword(q.query(), DEPTH).stream().map(names::get).toList();
            List<String> fused = hybrid.handle(q.query(), DEPTH).stream().map(SearchResult::name).toList();
            report.append(String.format(Locale.ROOT, "%-24s %-16s %5s %5s %5s%n", clip(q.query(), 22), clip(q.expected(), 14),
                    rank(nVec.record(vector, q.expected())), rank(nKw.record(keyword, q.expected())),
                    rank(nHyb.record(fused, q.expected()))));
        }
        report.append("\n").append(String.format(Locale.ROOT, "%-34s %8s %8s %8s%n", "name queries", "hit@1", "hit@3", "MRR"));
        report.append(nVec.line("vector")).append(nKw.line("keyword")).append(nHyb.line("hybrid"));

        System.out.println(report);
        Files.writeString(Path.of("target", "search-eval.txt"), report.toString());
    }

    // -- helpers ------------------------------------------------------------

    private Map<UUID, String> seedTemplates(List<TemplateSeed> seeds) throws Exception {
        Map<UUID, String> names = new HashMap<>();
        for (TemplateSeed seed : seeds) {
            TemplateId id = draft.handle(seed.name(), placeholderPng());
            reviseProfile.handle(id, new MemeProfile(seed.meaning(), seed.usageExamples(),
                    seed.emotions(), seed.aliases()));
            int slotNo = 1;
            for (SlotSeed s : seed.slots()) {
                defineSlot.handle(id, new Slot(slotNo++, s.role(), s.maxChars(), true,
                        s.x(), s.y(), s.width(), s.height()));
            }
            approve.handle(id);
            names.put(id.value(), seed.name());
        }
        return names;
    }

    private Map<String, Pieces> embedPieces(List<TemplateSeed> seeds) {
        Map<String, Pieces> result = new HashMap<>();
        for (TemplateSeed seed : seeds) {
            result.put(seed.name(), new Pieces(embeddings.embed(seed.meaning()),
                    seed.usageExamples().stream().map(embeddings::embed).toList()));
        }
        return result;
    }

    /** Cosine similarity between the query and each template's single stored vector. */
    private Map<String, Double> blobSimilarities(float[] query, Map<UUID, String> names) {
        Map<String, Double> sims = new HashMap<>();
        jdbc.query("SELECT template_id, embedding <=> ?::vector AS distance FROM template_search",
                rs -> {
                    sims.put(names.get(rs.getObject("template_id", UUID.class)), 1 - rs.getDouble("distance"));
                }, literal(query));
        return sims;
    }

    private static List<String> rankBy(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).limit(DEPTH).toList();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private <T> T read(String resource, TypeReference<T> type) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return json.readValue(in, type);
        }
    }

    private double nearestDistance(float[] vector) {
        return jdbc.queryForObject("SELECT min(embedding <=> ?::vector) FROM template_search",
                Double.class, literal(vector));
    }

    private static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(i == 0 ? "" : ",").append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static byte[] placeholderPng() throws Exception {
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 600, 600);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String rank(int rank) {
        return rank == 0 ? "miss" : String.valueOf(rank);
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String summary(List<Double> values) {
        if (values.isEmpty()) {
            return "n/a";
        }
        var stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        return String.format(Locale.ROOT, "min %.3f  avg %.3f  max %.3f", stats.getMin(), stats.getAverage(), stats.getMax());
    }

    private static final class Stats {
        private int total;
        private int hitAt1;
        private int hitAt3;
        private double reciprocalRankSum;

        /** @return 1-based rank of the expected template, or 0 when it is not in the list */
        int record(List<String> ranking, String expected) {
            int index = ranking.indexOf(expected);
            int rank = index < 0 ? 0 : index + 1;
            total++;
            if (rank == 1) {
                hitAt1++;
            }
            if (rank >= 1 && rank <= 3) {
                hitAt3++;
            }
            if (rank > 0) {
                reciprocalRankSum += 1.0 / rank;
            }
            return rank;
        }

        String line(String label) {
            return String.format(Locale.ROOT, "%-34s %7.0f%% %7.0f%% %8.3f%n", label,
                    100.0 * hitAt1 / total, 100.0 * hitAt3 / total, reciprocalRankSum / total);
        }
    }
}
