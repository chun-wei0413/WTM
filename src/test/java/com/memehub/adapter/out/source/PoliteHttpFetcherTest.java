package com.memehub.adapter.out.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memehub.application.collection.FetchRefusedException;
import com.memehub.application.port.out.RemoteFetchPort.FetchedImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Runs the fetcher against a small web server on this machine, which is why these tests allow
 * private addresses; one test checks that the default refuses them.
 */
class PoliteHttpFetcherTest {

    private HttpServer server;
    private String base;
    private final Map<String, Long> hitTimes = new ConcurrentHashMap<>();
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private volatile String lastUserAgent;
    private String robots = "User-agent: *\nDisallow: /private/\n";
    private int robotsStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        lastUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
        hitTimes.put(path, System.nanoTime());
        switch (path) {
            case "/robots.txt" -> reply(exchange, robotsStatus, "text/plain", robots.getBytes(StandardCharsets.UTF_8));
            case "/page" -> reply(exchange, 200, "text/html; charset=utf-8", "你好 hello".getBytes(StandardCharsets.UTF_8));
            case "/pic.png" -> reply(exchange, 200, "image/png", new byte[] {1, 2, 3, 4});
            case "/not-a-pic" -> reply(exchange, 200, "text/html", "<html>".getBytes());
            case "/big.png" -> reply(exchange, 200, "image/png", new byte[200 * 1024]);
            case "/redirect" -> {
                exchange.getResponseHeaders().add("Location", "/pic.png");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
            case "/loop" -> {
                exchange.getResponseHeaders().add("Location", "/loop");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
            case "/private/secret" -> reply(exchange, 200, "text/plain", "secret".getBytes());
            case "/gone" -> reply(exchange, 404, "text/plain", "no".getBytes());
            default -> reply(exchange, 404, "text/plain", "not found".getBytes());
        }
    }

    private static void reply(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        // A chunked reply (no Content-Length) for the big file, so the size cap itself is exercised.
        boolean chunked = exchange.getRequestURI().getPath().equals("/big.png");
        exchange.sendResponseHeaders(status, chunked ? 0 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private PoliteHttpFetcher fetcher(boolean allowPrivate, Duration minDelay) {
        return new PoliteHttpFetcher(new SourceProperties("memehub-test/1.0 (testing)", "tester@example.com",
                minDelay, Duration.ofSeconds(5), DataSize.ofKilobytes(64), DataSize.ofKilobytes(64), allowPrivate,
                new SourceProperties.Ptt(List.of("StupidClown"))));
    }

    @Test
    void readsATextPageAsUtf8AndIdentifiesItself() {
        String text = fetcher(true, Duration.ofMillis(10)).fetchText(base + "/page");

        assertThat(text).isEqualTo("你好 hello");
        assertThat(lastUserAgent).isEqualTo("memehub-test/1.0 (testing); tester@example.com");
    }

    @Test
    void readsAPictureTogetherWithItsType() {
        FetchedImage image = fetcher(true, Duration.ofMillis(10)).fetchImage(base + "/pic.png");

        assertThat(image.content()).containsExactly(1, 2, 3, 4);
        assertThat(image.contentType()).isEqualTo("image/png");
    }

    @Test
    void refusesSomethingThatIsNotAPicture() {
        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(10)).fetchImage(base + "/not-a-pic"))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("Not a picture");
    }

    @Test
    void refusesAFileLargerThanTheLimitEvenWhenTheSiteDoesNotSayHowBigItIs() {
        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(10)).fetchImage(base + "/big.png"))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("too large");
    }

    @Test
    void followsARedirect() {
        FetchedImage image = fetcher(true, Duration.ofMillis(10)).fetchImage(base + "/redirect");

        assertThat(image.content()).hasSize(4);
    }

    @Test
    void givesUpOnEndlessRedirects() {
        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(1)).fetchText(base + "/loop"))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("redirects");
    }

    @Test
    void obeysRobotsTxtAndNeverRequestsWhatItForbids() {
        PoliteHttpFetcher fetcher = fetcher(true, Duration.ofMillis(10));

        assertThatThrownBy(() -> fetcher.fetchText(base + "/private/secret"))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("robots.txt");
        assertThat(requestedPaths).doesNotContain("/private/secret");
        assertThat(fetcher.fetchText(base + "/page")).isNotBlank();
    }

    @Test
    void readsRobotsTxtOnlyOncePerSite() {
        PoliteHttpFetcher fetcher = fetcher(true, Duration.ofMillis(10));

        fetcher.fetchText(base + "/page");
        fetcher.fetchImage(base + "/pic.png");
        fetcher.fetchText(base + "/page");

        assertThat(requestedPaths.stream().filter("/robots.txt"::equals)).hasSize(1);
    }

    @Test
    void aSiteWithoutRobotsTxtHasNoRestrictions() {
        robotsStatus = 404;

        assertThat(fetcher(true, Duration.ofMillis(10)).fetchText(base + "/private/secret")).isEqualTo("secret");
    }

    @Test
    void aSiteWhoseRobotsTxtCannotBeReadIsLeftAlone() {
        robotsStatus = 500;

        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(10)).fetchText(base + "/page"))
                .isInstanceOf(FetchRefusedException.class);
        assertThat(requestedPaths).doesNotContain("/page");
    }

    @Test
    void anErrorAnswerBecomesARefusalNamingTheStatus() {
        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(10)).fetchText(base + "/gone"))
                .isInstanceOf(FetchRefusedException.class).hasMessageContaining("404");
    }

    @Test
    void waitsBetweenRequestsToTheSameSite() {
        PoliteHttpFetcher fetcher = fetcher(true, Duration.ofMillis(300));

        fetcher.fetchText(base + "/page");
        long first = hitTimes.get("/page");
        fetcher.fetchImage(base + "/pic.png");
        long second = hitTimes.get("/pic.png");

        assertThat(Duration.ofNanos(second - first)).isGreaterThanOrEqualTo(Duration.ofMillis(250));
    }

    @Test
    void honoursALongerCrawlDelayFromRobotsTxt() {
        robots = "User-agent: *\nCrawl-delay: 1\n";
        PoliteHttpFetcher fetcher = fetcher(true, Duration.ofMillis(10));

        fetcher.fetchText(base + "/page");          // the first request also had to read robots.txt
        fetcher.fetchText(base + "/page");
        fetcher.fetchImage(base + "/pic.png");
        long gap = hitTimes.get("/pic.png") - hitTimes.get("/page");

        assertThat(Duration.ofNanos(gap)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    void refusesPrivateAddressesByDefault() {
        PoliteHttpFetcher strict = fetcher(false, Duration.ofMillis(10));

        // Refused before anything is sent: the server on this machine never sees a request.
        assertThatThrownBy(() -> strict.fetchText(base + "/page"))
                .isInstanceOf(FetchRefusedException.class)
                .hasMessageMatching("(?s).*(private network|standard web ports).*");
        assertThat(requestedPaths).isEmpty();
    }

    @Test
    void refusesAnAddressThatIsNotValid() {
        assertThatThrownBy(() -> fetcher(true, Duration.ofMillis(10)).fetchText("not a url"))
                .isInstanceOf(FetchRefusedException.class);
    }
}
