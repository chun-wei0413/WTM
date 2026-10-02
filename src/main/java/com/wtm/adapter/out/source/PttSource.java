package com.wtm.adapter.out.source;

import com.wtm.application.collection.FetchRefusedException;
import com.wtm.application.port.out.MemeSourcePort;
import com.wtm.application.port.out.RemoteFetchPort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PTT boards: walks a board from its newest page backwards, opens each post and takes the pictures
 * linked in it (mostly on imgur). A board that asks for an age confirmation is never collected.
 * PTT publishes no robots.txt, so the collector's own politeness (a slow pace, a clear user agent)
 * is what applies.
 */
@Component
class PttSource implements MemeSourcePort {

    private static final Logger log = LoggerFactory.getLogger(PttSource.class);

    static final String BASE = "https://www.ptt.cc";
    private static final int MAX_INDEX_PAGES = 30;
    private static final Pattern BOARD_NAME = Pattern.compile("[A-Za-z0-9_-]{1,30}");
    private static final Pattern IMGUR = Pattern.compile(
            "^https?://(?:i\\.|m\\.)?imgur\\.com/([A-Za-z0-9]{5,8})(?:\\.(jpg|jpeg|png|gif))?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT = Pattern.compile(
            "^https?://\\S+\\.(?:jpg|jpeg|png|gif)(?:[?#]\\S*)?$", Pattern.CASE_INSENSITIVE);

    private final RemoteFetchPort fetcher;
    private final SourceProperties properties;

    PttSource(RemoteFetchPort fetcher, SourceProperties properties) {
        this.fetcher = fetcher;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "PTT";
    }

    @Override
    public String displayName() {
        return "PTT";
    }

    @Override
    public String description() {
        return "批踢踢看板,最貼近台灣的用語與梗。會一篇一篇讀文章、取出裡面的圖片連結,速度很慢(每次請求間隔 2 秒以上)。需要年齡確認的看板不會收集。";
    }

    @Override
    public List<SourceOption> options() {
        List<String> boards = properties.ptt().boards();
        return List.of(new SourceOption("board", "看板名稱(例如 " + String.join("、", boards) + ")",
                boards.isEmpty() ? "" : boards.get(0)));
    }

    @Override
    public Iterator<RemoteMeme> discover(Map<String, String> options) {
        String board = options.getOrDefault("board", properties.ptt().boards().isEmpty() ? "" : properties.ptt().boards().get(0)).strip();
        if (!BOARD_NAME.matcher(board).matches()) {
            throw new IllegalArgumentException("Please give a board name (letters, digits, '_' or '-')");
        }
        return new BoardIterator(board);
    }

    private final class BoardIterator implements Iterator<RemoteMeme> {

        private final String board;
        private final Deque<String> postUrls = new ArrayDeque<>();
        private final Deque<RemoteMeme> buffer = new ArrayDeque<>();
        private String nextIndexUrl;
        private int pagesRead;

        BoardIterator(String board) {
            this.board = board;
            this.nextIndexUrl = BASE + "/bbs/" + board + "/index.html";
        }

        @Override
        public boolean hasNext() {
            while (buffer.isEmpty()) {
                if (!postUrls.isEmpty()) {
                    loadPost(postUrls.poll());
                } else if (nextIndexUrl != null && pagesRead < MAX_INDEX_PAGES) {
                    loadIndex();
                } else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public RemoteMeme next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return buffer.poll();
        }

        private void loadIndex() {
            Document page = Jsoup.parse(fetcher.fetchText(nextIndexUrl), BASE);
            pagesRead++;
            if (page.selectFirst("form[action*=over18]") != null) {
                throw new FetchRefusedException("The board '" + board + "' asks for an age confirmation, so it is not collected");
            }
            List<String> links = new ArrayList<>(postLinks(page));
            Collections.reverse(links);          // a board page lists oldest first; we want the newest first
            postUrls.addAll(links);
            nextIndexUrl = previousPage(page).orElse(null);
        }

        private void loadPost(String url) {
            Document post;
            try {
                post = Jsoup.parse(fetcher.fetchText(url), BASE);
            } catch (FetchRefusedException e) {
                log.info("PTT post {} skipped: {}", url, e.getMessage());    // deleted, or not allowed: go on to the next
                return;
            }
            Element body = post.selectFirst("#main-content");
            if (body == null) {
                return;
            }
            String title = metaValue(post, 1);
            String author = metaValue(post, 0);
            String credit = "PTT " + board + (author.isEmpty() ? "" : " · " + author.replaceAll("\\s*\\(.*\\)$", ""))
                    + (title.isEmpty() ? "" : " · " + title);
            Set<String> seen = new LinkedHashSet<>();
            for (Element link : body.select("a[href]")) {
                imageUrlFrom(link.attr("href")).filter(seen::add).ifPresent(imageUrl ->
                        buffer.add(new RemoteMeme(imageUrl, url, null, credit,
                                "Posted on PTT; the rights belong to the original creators")));
            }
        }
    }

    static List<String> postLinks(Document indexPage) {
        List<String> links = new ArrayList<>();
        for (Element a : indexPage.select("div.r-ent div.title a[href]")) {
            String href = a.attr("href");
            if (href.matches("/bbs/[^/]+/M\\.\\d+\\.A\\.[0-9A-Fa-f]+\\.html")) {
                links.add(BASE + href);
            }
        }
        return links;
    }

    static Optional<String> previousPage(Document indexPage) {
        for (Element a : indexPage.select("div.btn-group-paging a[href]")) {
            if (a.text().contains("上頁")) {
                return Optional.of(BASE + a.attr("href"));
            }
        }
        return Optional.empty();
    }

    /** The text of the n-th line of a post's header (0 = author, 1 = title), or an empty string. */
    private static String metaValue(Document post, int index) {
        var values = post.select("div.article-metaline span.article-meta-value");
        return index < values.size() ? values.get(index).text().strip() : "";
    }

    /** The address of a picture a post links to, or empty when the link is not a single picture. */
    static Optional<String> imageUrlFrom(String href) {
        Matcher imgur = IMGUR.matcher(href);
        if (imgur.matches()) {
            String extension = imgur.group(2) == null ? "jpg" : imgur.group(2).toLowerCase();
            return Optional.of("https://i.imgur.com/" + imgur.group(1) + "." + extension);
        }
        return DIRECT.matcher(href).matches() ? Optional.of(href) : Optional.empty();
    }
}
