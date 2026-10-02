package com.wtm.adapter.out.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.collection.FetchRefusedException;
import com.wtm.application.port.out.MemeSourcePort;
import com.wtm.application.port.out.RemoteFetchPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * Wikimedia Commons: meme pictures with an openly stated licence, whose author and licence are
 * recorded with each one. The listing goes through Wikimedia's API, which has its own etiquette
 * (an identifying user agent, requests one after another, {@code maxlag}); robots.txt, which
 * forbids crawlers from /w/, is about crawling pages, not about this API. The pictures themselves
 * are downloaded from upload.wikimedia.org under robots.txt as usual.
 */
@Component
class WikimediaSource implements MemeSourcePort {

    static final String API = "https://commons.wikimedia.org/w/api.php";
    private static final Set<String> PICTURE_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    private final RemoteFetchPort fetcher;
    private final ObjectMapper json;

    WikimediaSource(RemoteFetchPort fetcher, ObjectMapper json) {
        this.fetcher = fetcher;
        this.json = json;
    }

    @Override
    public String id() {
        return "WIKIMEDIA";
    }

    @Override
    public String displayName() {
        return "Wikimedia Commons";
    }

    @Override
    public String description() {
        return "開放授權的迷因圖,每張都會記錄作者與授權條款。數量不多,但授權最清楚。";
    }

    @Override
    public List<SourceOption> options() {
        return List.of(new SourceOption("category", "分類名稱", "Internet_memes"));
    }

    @Override
    public Iterator<RemoteMeme> discover(Map<String, String> options) {
        String category = options.getOrDefault("category", "Internet_memes").strip()
                .replaceFirst("(?i)^Category:", "").replace(' ', '_');
        if (category.isEmpty()) {
            category = "Internet_memes";
        }
        return new PagedIterator(category);
    }

    /** Asks for one page of the category at a time, only when the previous page has been used up. */
    private final class PagedIterator implements Iterator<RemoteMeme> {

        private final String category;
        private final Deque<RemoteMeme> buffer = new ArrayDeque<>();
        private String continuation = "";
        private boolean exhausted;

        PagedIterator(String category) {
            this.category = category;
        }

        @Override
        public boolean hasNext() {
            while (buffer.isEmpty() && !exhausted) {
                loadPage();
            }
            return !buffer.isEmpty();
        }

        @Override
        public RemoteMeme next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return buffer.poll();
        }

        private void loadPage() {
            String url = API + "?action=query&format=json&formatversion=2&generator=categorymembers"
                    + "&gcmtitle=" + encode("Category:" + category) + "&gcmtype=file&gcmlimit=50"
                    + "&prop=imageinfo&iiprop=" + encode("url|extmetadata|mime") + "&iiurlwidth=1280"
                    + "&maxlag=5" + continuation;   // maxlag: a polite bot steps back when the servers are busy
            JsonNode root;
            try {
                root = json.readTree(fetcher.fetchApi(url));
            } catch (JsonProcessingException e) {
                throw new FetchRefusedException("Wikimedia's answer could not be read", e);
            }
            if (root.has("error")) {
                throw new FetchRefusedException("Wikimedia refused: " + root.path("error").path("info").asText(""));
            }
            for (JsonNode page : root.path("query").path("pages")) {
                toMeme(page);
            }
            if (root.has("continue")) {
                StringBuilder next = new StringBuilder();
                root.get("continue").fields().forEachRemaining(
                        e -> next.append('&').append(encode(e.getKey())).append('=').append(encode(e.getValue().asText())));
                continuation = next.toString();
            } else {
                exhausted = true;
            }
        }

        private void toMeme(JsonNode page) {
            JsonNode info = page.path("imageinfo").path(0);
            if (!PICTURE_TYPES.contains(info.path("mime").asText(""))) {
                return;
            }
            String imageUrl = info.hasNonNull("thumburl") ? info.get("thumburl").asText() : info.path("url").asText("");
            if (imageUrl.isBlank()) {
                return;
            }
            JsonNode meta = info.path("extmetadata");
            String artist = plainText(meta.path("Artist").path("value").asText(""));
            String licence = meta.path("LicenseShortName").path("value").asText("");
            String licenceUrl = meta.path("LicenseUrl").path("value").asText("");
            String title = page.path("title").asText("").replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z0-9]{2,5}$", "");
            buffer.add(new RemoteMeme(imageUrl, info.path("descriptionurl").asText(null), title.isBlank() ? null : title,
                    (artist.isBlank() ? "" : artist + " · ") + "Wikimedia Commons",
                    (licence + " " + licenceUrl).strip().isEmpty() ? null : (licence + " " + licenceUrl).strip()));
        }
    }

    private static String plainText(String html) {
        return html.isBlank() ? "" : Jsoup.parse(html).text().strip();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
