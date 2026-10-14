package com.wtm.application.template.search;

import com.wtm.application.port.out.TemplateSearchPort.Hit;
import com.wtm.application.port.out.TemplateSearchPort.IndexedText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword ranking by BM25 (Robertson and Zaragoza, 2009): for each word of the query, how rare the word is in the library
 * times how often it occurs in the text, with repeated occurrences counting less and less and long texts counting less.
 *
 * <p>Chinese has no spaces between words and no word list is needed here: every run of Chinese characters is cut into
 * pieces of two characters that overlap ("老闆又改需求" becomes 老闆, 闆又, 又改, 改需, 需求), and a query is cut the same way, so a
 * query matches the texts that contain the same pieces. Letters and digits are kept as lower-case words.
 */
public final class Bm25Index {

    /** How quickly repeated words stop adding to the score. */
    public static final double K1 = 1.2;
    /** How much a long text is held against its length (0 = not at all, 1 = fully). */
    public static final double B = 0.75;

    private static final Pattern WORD = Pattern.compile("[a-z0-9']+");
    private static final Pattern CHINESE_RUN = Pattern.compile("[\\u4e00-\\u9fff]+");

    private record Posting(int document, int count) {
    }

    private final List<UUID> ids = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final double averageLength;

    public Bm25Index(List<IndexedText> texts) {
        long total = 0;
        for (IndexedText text : texts) {
            int document = ids.size();
            Map<String, Integer> counts = new HashMap<>();
            int length = 0;
            for (String token : tokens(text.text())) {
                counts.merge(token, 1, Integer::sum);
                length++;
            }
            ids.add(text.id());
            lengths.add(length);
            total += length;
            counts.forEach((token, count) ->
                    postings.computeIfAbsent(token, t -> new ArrayList<>()).add(new Posting(document, count)));
        }
        this.averageLength = ids.isEmpty() ? 0 : (double) total / ids.size();
    }

    public int size() {
        return ids.size();
    }

    /** The memes that share words with the query, best first; none when nothing is shared. */
    public List<Hit> search(String query, int limit) {
        if (ids.isEmpty() || limit <= 0) {
            return List.of();
        }
        double[] scores = new double[ids.size()];
        int n = ids.size();
        for (String term : new HashSet<>(tokens(query))) {
            List<Posting> list = postings.get(term);
            if (list == null) {
                continue;
            }
            double idf = Math.log(1 + (n - list.size() + 0.5) / (list.size() + 0.5));
            for (Posting posting : list) {
                double lengthPart = K1 * (1 - B + B * lengths.get(posting.document()) / averageLength);
                scores[posting.document()] += idf * posting.count() * (K1 + 1) / (posting.count() + lengthPart);
            }
        }
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > 0) {
                hits.add(new Hit(ids.get(i), scores[i]));
            }
        }
        // the sort is stable, so memes with the same score stay in the order of the library
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits);
    }

    /** Lower-case words, plus overlapping two-character pieces of every run of Chinese characters. */
    public static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher words = WORD.matcher(lower);
        while (words.find()) {
            tokens.add(words.group());
        }
        Matcher runs = CHINESE_RUN.matcher(lower);
        while (runs.find()) {
            String run = runs.group();
            if (run.length() == 1) {
                tokens.add(run);
            }
            for (int i = 0; i + 1 < run.length(); i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        return tokens;
    }
}
