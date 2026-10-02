package com.usethatmeme.adapter.out.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.application.collection.FetchRefusedException;
import com.usethatmeme.application.port.out.MemeSourcePort.RemoteMeme;
import com.usethatmeme.application.port.out.RemoteFetchPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * The three sources read from pages that look like the real ones. A fake fetcher hands out
 * prepared pages and remembers which addresses were asked for.
 */
class SourcesTest {

    /** Serves prepared text by address and records every request, in order. */
    private static class FakeFetcher implements RemoteFetchPort {
        final Map<String, String> pages = new LinkedHashMap<>();
        final List<String> requested = new ArrayList<>();

        @Override
        public String fetchText(String url) {
            requested.add(url);
            String page = pages.get(url);
            if (page == null) {
                throw new FetchRefusedException("The site answered with HTTP 404");
            }
            return page;
        }

        @Override
        public String fetchApi(String url) {
            return fetchText(url);
        }

        @Override
        public FetchedImage fetchImage(String url) {
            throw new UnsupportedOperationException();
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final FakeFetcher fetcher = new FakeFetcher();

    private static List<RemoteMeme> all(Iterator<RemoteMeme> items) {
        List<RemoteMeme> list = new ArrayList<>();
        items.forEachRemaining(list::add);
        return list;
    }

    // ---- Imgflip -----------------------------------------------------------------------------

    @Test
    void imgflipListsItsTemplatesWithNamesAndCredit() {
        fetcher.pages.put(ImgflipSource.LIST_URL, """
                {"success": true, "data": {"memes": [
                  {"id": "181913649", "name": "Drake Hotline Bling", "url": "https://i.imgflip.com/30b1gx.jpg", "width": 1200, "height": 1200, "box_count": 2},
                  {"id": "87743020", "name": "Two Buttons", "url": "https://i.imgflip.com/1g8my4.jpg"},
                  {"id": "1", "name": "No picture", "url": ""}]}}""");

        List<RemoteMeme> memes = all(new ImgflipSource(fetcher, json).discover(Map.of()));

        assertThat(memes).hasSize(2);
        assertThat(memes.get(0).title()).isEqualTo("Drake Hotline Bling");
        assertThat(memes.get(0).imageUrl()).isEqualTo("https://i.imgflip.com/30b1gx.jpg");
        assertThat(memes.get(0).pageUrl()).isEqualTo("https://imgflip.com/memetemplate/181913649");
        assertThat(memes.get(0).attribution()).isEqualTo("Imgflip");
    }

    @Test
    void imgflipReportsAnErrorFromTheSiteAndAnUnreadableAnswer() {
        fetcher.pages.put(ImgflipSource.LIST_URL, "{\"success\": false, \"error_message\": \"slow down\"}");
        assertThatThrownBy(() -> new ImgflipSource(fetcher, json).discover(Map.of()))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("slow down");

        fetcher.pages.put(ImgflipSource.LIST_URL, "<html>oops</html>");
        assertThatThrownBy(() -> new ImgflipSource(fetcher, json).discover(Map.of()))
                .isInstanceOf(FetchRefusedException.class);
    }

    // ---- Wikimedia Commons ---------------------------------------------------------------------

    private static String wikimediaPage(String title, String mime, String url, String artist, String licence, String extra) {
        return """
                {"title": "File:%s", "imageinfo": [{"mime": "%s", "url": "%s", "thumburl": "%s",
                  "descriptionurl": "https://commons.wikimedia.org/wiki/File:%s",
                  "extmetadata": {"Artist": {"value": "%s"}, "LicenseShortName": {"value": "%s"},
                                  "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0"}}}]}%s"""
                .formatted(title, mime, url + "-full", url, title, artist, licence, extra);
    }

    @Test
    void wikimediaReadsPictureLicenceAndAuthorAndSkipsOtherFileTypes() {
        WikimediaSource source = new WikimediaSource(fetcher, json);
        String url = WikimediaSource.API + "?action=query&format=json&formatversion=2&generator=categorymembers"
                + "&gcmtitle=Category%3AInternet_memes&gcmtype=file&gcmlimit=50&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=1280&maxlag=5";
        fetcher.pages.put(url, """
                {"query": {"pages": [%s, %s, %s]}}""".formatted(
                wikimediaPage("Cat staring.jpg", "image/jpeg", "https://upload.example/cat.jpg",
                        "<a href=\\\"x\\\">Jane Doe</a>", "CC BY-SA 4.0", ""),
                wikimediaPage("Sound.ogg", "audio/ogg", "https://upload.example/sound.ogg", "x", "CC0", ""),
                wikimediaPage("Diagram.svg", "image/svg+xml", "https://upload.example/d.svg", "x", "CC0", "")));

        List<RemoteMeme> memes = all(source.discover(Map.of()));

        assertThat(memes).hasSize(1);
        RemoteMeme cat = memes.get(0);
        assertThat(cat.title()).isEqualTo("Cat staring");
        assertThat(cat.imageUrl()).isEqualTo("https://upload.example/cat.jpg");
        assertThat(cat.attribution()).isEqualTo("Jane Doe · Wikimedia Commons");
        assertThat(cat.licenseNote()).startsWith("CC BY-SA 4.0 ").contains("creativecommons.org");
        assertThat(cat.pageUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:Cat staring.jpg");
    }

    @Test
    void wikimediaAsksForTheNextPageOnlyWhenTheFirstIsUsedUp() {
        WikimediaSource source = new WikimediaSource(fetcher, json);
        String first = WikimediaSource.API + "?action=query&format=json&formatversion=2&generator=categorymembers"
                + "&gcmtitle=Category%3AMemes_of_cats&gcmtype=file&gcmlimit=50&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=1280&maxlag=5";
        fetcher.pages.put(first, """
                {"continue": {"gcmcontinue": "file|ABC|1", "continue": "gcmcontinue||"},
                 "query": {"pages": [%s]}}""".formatted(
                wikimediaPage("One.png", "image/png", "https://upload.example/1.png", "A", "CC0", "")));
        fetcher.pages.put(first + "&gcmcontinue=file%7CABC%7C1&continue=gcmcontinue%7C%7C", """
                {"query": {"pages": [%s]}}""".formatted(
                wikimediaPage("Two.png", "image/png", "https://upload.example/2.png", "B", "CC0", "")));

        Iterator<RemoteMeme> items = source.discover(Map.of("category", "Category:Memes of cats"));

        assertThat(items.next().title()).isEqualTo("One");
        assertThat(fetcher.requested).as("the second page is not read yet").hasSize(1);
        assertThat(items.next().title()).isEqualTo("Two");
        assertThat(fetcher.requested).hasSize(2);
        assertThat(items.hasNext()).isFalse();
    }

    @Test
    void wikimediaReportsAnErrorFromTheApi() {
        FakeFetcher failing = new FakeFetcher() {
            @Override
            public String fetchApi(String url) {
                return "{\"error\": {\"info\": \"bad category\"}}";
            }
        };

        assertThatThrownBy(() -> new WikimediaSource(failing, json).discover(Map.of()).hasNext())
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("bad category");
    }

    // ---- PTT -------------------------------------------------------------------------------------

    private SourceProperties pttProperties() {
        return new SourceProperties("usethatmeme-test/1.0", "", Duration.ofMillis(1), Duration.ofSeconds(5),
                DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), false, new SourceProperties.Ptt(List.of("StupidClown", "C_Chat")));
    }

    private static String indexPage(String paging, String... posts) {
        StringBuilder sb = new StringBuilder("<html><body><div class=\"btn-group btn-group-paging\">").append(paging)
                .append("</div>");
        for (String post : posts) {
            sb.append("<div class=\"r-ent\"><div class=\"title\"><a href=\"").append(post).append("\">title</a></div></div>");
        }
        return sb.append("</body></html>").toString();
    }

    private static String postPage(String author, String title, String... links) {
        StringBuilder body = new StringBuilder();
        for (String link : links) {
            body.append("<a href=\"").append(link).append("\">").append(link).append("</a>\n");
        }
        return "<html><body><div id=\"main-content\">"
                + "<div class=\"article-metaline\"><span class=\"article-meta-tag\">作者</span><span class=\"article-meta-value\">" + author + "</span></div>"
                + "<div class=\"article-metaline\"><span class=\"article-meta-tag\">標題</span><span class=\"article-meta-value\">" + title + "</span></div>"
                + body + "</div></body></html>";
    }

    @Test
    void pttWalksABoardNewestFirstAndTakesTheImagesLinkedInEachPost() {
        String board = PttSource.BASE + "/bbs/StupidClown";
        fetcher.pages.put(board + "/index.html", indexPage(
                "<a class=\"btn wide\" href=\"/bbs/StupidClown/index1.html\">最舊</a>"
                        + "<a class=\"btn wide\" href=\"/bbs/StupidClown/index99.html\">‹ 上頁</a>",
                "/bbs/StupidClown/M.1700000001.A.AAA.html", "/bbs/StupidClown/M.1700000002.A.BBB.html"));
        fetcher.pages.put(board + "/M.1700000002.A.BBB.html", postPage("someone (小明)", "[笨版] 好笑",
                "https://i.imgur.com/AbCdE12.jpg", "https://imgur.com/XyZ98765", "https://example.com/not-an-image.html"));
        fetcher.pages.put(board + "/M.1700000001.A.AAA.html", postPage("other", "no images here", "https://example.com/page"));
        fetcher.pages.put(board + "/index99.html", indexPage("", "/bbs/StupidClown/M.1699999999.A.CCC.html"));
        fetcher.pages.put(board + "/M.1699999999.A.CCC.html", postPage("third", "older", "https://i.imgur.com/QqQqQ99.png"));
        PttSource source = new PttSource(fetcher, pttProperties());

        Iterator<RemoteMeme> items = source.discover(Map.of("board", "StupidClown"));
        RemoteMeme first = items.next();

        assertThat(first.imageUrl()).as("the newest post is read first").isEqualTo("https://i.imgur.com/AbCdE12.jpg");
        assertThat(first.pageUrl()).isEqualTo(board + "/M.1700000002.A.BBB.html");
        assertThat(first.attribution()).isEqualTo("PTT StupidClown · someone · [笨版] 好笑");
        assertThat(first.title()).as("a post title is no name for a picture").isNull();
        assertThat(fetcher.requested).containsExactly(board + "/index.html", board + "/M.1700000002.A.BBB.html");

        assertThat(items.next().imageUrl()).isEqualTo("https://i.imgur.com/XyZ98765.jpg");
        assertThat(items.next().imageUrl()).as("after the empty post, the previous page is read").isEqualTo("https://i.imgur.com/QqQqQ99.png");
        assertThat(items.hasNext()).isFalse();
    }

    @Test
    void pttNeverCollectsABoardThatAsksForAnAgeConfirmation() {
        fetcher.pages.put(PttSource.BASE + "/bbs/Gossiping/index.html",
                "<html><body><form action=\"/ask/over18\" method=\"post\"></form></body></html>");

        Iterator<RemoteMeme> items = new PttSource(fetcher, pttProperties()).discover(Map.of("board", "Gossiping"));

        assertThatThrownBy(items::hasNext).isInstanceOf(FetchRefusedException.class).hasMessageContaining("age confirmation");
    }

    @Test
    void pttSkipsAPostThatCannotBeReadAndGoesOn() {
        String board = PttSource.BASE + "/bbs/C_Chat";
        fetcher.pages.put(board + "/index.html", indexPage("",
                "/bbs/C_Chat/M.1700000001.A.AAA.html", "/bbs/C_Chat/M.1700000002.A.BBB.html"));
        fetcher.pages.put(board + "/M.1700000001.A.AAA.html", postPage("a", "ok", "https://i.imgur.com/GoodOne1.jpg"));
        // the newer post is not in the fake site: reading it fails, like a deleted post

        Iterator<RemoteMeme> items = new PttSource(fetcher, pttProperties()).discover(Map.of("board", "C_Chat"));

        assertThat(items.next().imageUrl()).isEqualTo("https://i.imgur.com/GoodOne1.jpg");
    }

    @Test
    void pttUsesTheFirstConfiguredBoardAndRejectsOddBoardNames() {
        PttSource source = new PttSource(fetcher, pttProperties());
        fetcher.pages.put(PttSource.BASE + "/bbs/StupidClown/index.html", indexPage(""));

        assertThat(source.discover(Map.of()).hasNext()).isFalse();
        assertThat(fetcher.requested).containsExactly(PttSource.BASE + "/bbs/StupidClown/index.html");
        assertThatThrownBy(() -> source.discover(Map.of("board", "../etc/passwd"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.discover(Map.of("board", ""))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recognisesImageLinksAndIgnoresTheRest() {
        assertThat(PttSource.imageUrlFrom("https://i.imgur.com/AbCdE12.jpg")).contains("https://i.imgur.com/AbCdE12.jpg");
        assertThat(PttSource.imageUrlFrom("http://imgur.com/AbCdE12")).contains("https://i.imgur.com/AbCdE12.jpg");
        assertThat(PttSource.imageUrlFrom("https://i.imgur.com/AbCdE12.GIF?x=1")).contains("https://i.imgur.com/AbCdE12.gif");
        assertThat(PttSource.imageUrlFrom("https://example.com/path/pic.PNG")).contains("https://example.com/path/pic.PNG");
        assertThat(PttSource.imageUrlFrom("https://imgur.com/a/AlbumId")).isEmpty();
        assertThat(PttSource.imageUrlFrom("https://imgur.com/gallery/AbCdE12")).isEmpty();
        assertThat(PttSource.imageUrlFrom("https://example.com/article.html")).isEmpty();
        assertThat(PttSource.imageUrlFrom("https://www.youtube.com/watch?v=abc")).isEmpty();
    }

    @Test
    void findsPostLinksAndThePreviousPageInABoardIndex() {
        var page = Jsoup.parse(indexPage("<a class=\"btn wide\" href=\"/bbs/X/index5.html\">‹ 上頁</a>",
                "/bbs/X/M.1.A.AB.html", "/bbs/X/not-a-post.html"), PttSource.BASE);

        assertThat(PttSource.postLinks(page)).containsExactly(PttSource.BASE + "/bbs/X/M.1.A.AB.html");
        assertThat(PttSource.previousPage(page)).contains(PttSource.BASE + "/bbs/X/index5.html");
    }
}
