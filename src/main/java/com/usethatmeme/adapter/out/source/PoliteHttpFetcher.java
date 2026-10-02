package com.usethatmeme.adapter.out.source;

import com.usethatmeme.application.collection.FetchRefusedException;
import com.usethatmeme.application.port.out.RemoteFetchPort;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Downloads from other websites without being a bad guest: it reads robots.txt and obeys it,
 * waits between requests to the same site, refuses addresses on private networks (also after
 * redirects), and never reads more than a set number of bytes.
 */
@Component
class PoliteHttpFetcher implements RemoteFetchPort {

    private static final int MAX_REDIRECTS = 5;
    private static final long ROBOTS_MAX_BYTES = 512 * 1024;
    private static final Duration ROBOTS_TTL = Duration.ofHours(1);
    private static final Duration ROBOTS_ERROR_TTL = Duration.ofMinutes(5);
    private static final Duration LONGEST_CRAWL_DELAY = Duration.ofSeconds(10);

    private record Response(int status, byte[] body, String contentType) {
    }

    private record CachedRobots(RobotsTxt robots, long expiresAtNanos) {
    }

    private final SourceProperties properties;
    private final HttpClient http;
    private final ConcurrentHashMap<String, Long> nextRequestAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedRobots> robotsByHost = new ConcurrentHashMap<>();

    PoliteHttpFetcher(SourceProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)   // redirects are followed by hand, and checked
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String fetchText(String url) {
        Response response = get(url, properties.maxTextSize().toBytes(), true);
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Override
    public String fetchApi(String url) {
        Response response = get(url, properties.maxTextSize().toBytes(), false);
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Override
    public FetchedImage fetchImage(String url) {
        Response response = get(url, properties.maxImageSize().toBytes(), true);
        String type = response.contentType() == null ? "" : response.contentType().toLowerCase(Locale.ROOT);
        if (!type.startsWith("image/")) {
            throw new FetchRefusedException("Not a picture (the site said it is '" + type + "')");
        }
        return new FetchedImage(response.body(), type);
    }

    private Response get(String url, long maxBytes, boolean obeyRobots) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new FetchRefusedException("Not a valid address: " + url, e);
        }
        Response response = request(uri, maxBytes, obeyRobots);
        if (response.status() != 200) {
            throw new FetchRefusedException("The site answered with HTTP " + response.status());
        }
        return response;
    }

    /** One request, following up to five redirects; each address on the way is checked like the first. */
    private Response request(URI start, long maxBytes, boolean obeyRobots) {
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            AddressGuard.requireAllowed(uri, properties.allowPrivateAddresses());
            if (obeyRobots && !robotsAllow(uri)) {
                throw new FetchRefusedException("The site's robots.txt does not allow fetching " + uri.getRawPath());
            }
            throttle(uri);
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new FetchRefusedException("A redirect without a destination"));
                closeQuietly(response.body());
                uri = uri.resolve(location);
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                return new Response(status, new byte[0], null);
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > maxBytes) {
                closeQuietly(response.body());
                throw new FetchRefusedException("The file is too large (" + declared + " bytes)");
            }
            byte[] body = readCapped(response.body(), maxBytes);
            return new Response(status, body, response.headers().firstValue("Content-Type").orElse(null));
        }
        throw new FetchRefusedException("Too many redirects");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.timeout())
                .header("User-Agent", properties.fullUserAgent())
                .header("Accept", "*/*")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new FetchRefusedException("Could not reach " + uri.getHost() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchRefusedException("Interrupted while downloading", e);
        }
    }

    private static byte[] readCapped(InputStream body, long maxBytes) {
        try (body) {
            byte[] bytes = body.readNBytes((int) maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new FetchRefusedException("The file is too large (more than " + maxBytes + " bytes)");
            }
            return bytes;
        } catch (IOException e) {
            throw new FetchRefusedException("The download was interrupted: " + e.getMessage(), e);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    // -- politeness ---------------------------------------------------------------------------

    /** Waits until at least the minimum delay has passed since the last request to the same site. */
    private void throttle(URI uri) {
        String host = hostKey(uri);
        long delayNanos = delayFor(uri).toNanos();
        long wait;
        synchronized (nextRequestAt) {
            long now = System.nanoTime();
            long allowedAt = nextRequestAt.getOrDefault(host, now);
            wait = Math.max(0, allowedAt - now);
            nextRequestAt.put(host, Math.max(now, allowedAt) + delayNanos);
        }
        if (wait > 0) {
            try {
                Thread.sleep(Duration.ofNanos(wait));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchRefusedException("Interrupted while waiting between requests", e);
            }
        }
    }

    private Duration delayFor(URI uri) {
        Duration asked = Optional.ofNullable(robotsByHost.get(hostKey(uri)))
                .flatMap(c -> c.robots().crawlDelay(properties.productToken()))
                .orElse(Duration.ZERO);
        Duration wanted = asked.compareTo(properties.minDelay()) > 0 ? asked : properties.minDelay();
        return wanted.compareTo(LONGEST_CRAWL_DELAY) > 0 ? LONGEST_CRAWL_DELAY : wanted;
    }

    private boolean robotsAllow(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String pathAndQuery = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        return robotsFor(uri).isAllowed(properties.productToken(), pathAndQuery);
    }

    private RobotsTxt robotsFor(URI uri) {
        String host = hostKey(uri);
        CachedRobots cached = robotsByHost.get(host);
        long now = System.nanoTime();
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.robots();
        }
        URI robotsUri = URI.create(uri.getScheme() + "://" + host + "/robots.txt");
        RobotsTxt robots;
        Duration ttl = ROBOTS_TTL;
        try {
            Response response = request(robotsUri, ROBOTS_MAX_BYTES, false);
            if (response.status() == 200) {
                robots = RobotsTxt.parse(new String(response.body(), StandardCharsets.UTF_8));
            } else if (response.status() >= 500 || response.status() == 429) {
                robots = RobotsTxt.denyEverything();      // cannot tell what is allowed, so assume nothing is
                ttl = ROBOTS_ERROR_TTL;
            } else {
                robots = RobotsTxt.allowEverything();     // no robots.txt means no restrictions
            }
        } catch (FetchRefusedException e) {
            robots = RobotsTxt.denyEverything();
            ttl = ROBOTS_ERROR_TTL;
        }
        robotsByHost.put(host, new CachedRobots(robots, now + ttl.toNanos()));
        return robots;
    }

    private static String hostKey(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getPort() == -1 ? host : host + ":" + uri.getPort();
    }
}
