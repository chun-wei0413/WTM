package com.wtm.adapter.out.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    private static final String US = "wtm-personal-collector";

    @Test
    void everythingIsAllowedWhenNothingForbidsIt() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nDisallow: /emails/activate\n");

        assertThat(robots.isAllowed(US, "/bbs/anything")).isTrue();
        assertThat(robots.isAllowed(US, "/emails/activate")).isFalse();
        assertThat(robots.isAllowed(US, "/emails/activate/now?x=1")).isFalse();
    }

    @Test
    void anEmptyDisallowAllowsEverything() {
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow:\n").isAllowed(US, "/any/path")).isTrue();
    }

    @Test
    void disallowingTheRootForbidsEverything() {
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow: /\n").isAllowed(US, "/")).isFalse();
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow: /\n").isAllowed(US, "/a/b")).isFalse();
    }

    @Test
    void theLongestMatchingRuleWinsAndAnAllowBeatsADisallowOfTheSameLength() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /private/
                Allow: /private/public/
                Disallow: /same
                Allow: /same
                """);

        assertThat(robots.isAllowed(US, "/private/secret.html")).isFalse();
        assertThat(robots.isAllowed(US, "/private/public/page.html")).isTrue();
        assertThat(robots.isAllowed(US, "/same")).isTrue();
    }

    @Test
    void aSpecificGroupForUsReplacesTheWildcardGroup() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /
                User-agent: wtm
                Disallow: /admin/
                """);

        assertThat(robots.isAllowed(US, "/pictures/1.jpg")).isTrue();
        assertThat(robots.isAllowed(US, "/admin/panel")).isFalse();
        assertThat(robots.isAllowed("someone-else", "/pictures/1.jpg")).isFalse();
    }

    @Test
    void severalUserAgentLinesShareOneGroup() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: googlebot
                User-agent: wtm
                Disallow: /nope/
                """);

        assertThat(robots.isAllowed(US, "/nope/x")).isFalse();
        assertThat(robots.isAllowed(US, "/ok/x")).isTrue();
    }

    @Test
    void wildcardsAndEndAnchorsWork() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /*.json$
                Disallow: /search?*q=
                """);

        assertThat(robots.isAllowed(US, "/data/list.json")).isFalse();
        assertThat(robots.isAllowed(US, "/data/list.json.txt")).isTrue();
        assertThat(robots.isAllowed(US, "/search?page=2&q=cat")).isFalse();
        assertThat(robots.isAllowed(US, "/search")).isTrue();
    }

    @Test
    void commentsBlankLinesAndUnknownFieldsAreIgnored() {
        RobotsTxt robots = RobotsTxt.parse("""
                # a comment
                Sitemap: https://example.com/sitemap.xml

                User-agent: *   # everyone
                Disallow: /x/   # not here
                Host: example.com
                """);

        assertThat(robots.isAllowed(US, "/x/1")).isFalse();
        assertThat(robots.isAllowed(US, "/y/1")).isTrue();
    }

    @Test
    void fieldNamesAreNotCaseSensitive() {
        assertThat(RobotsTxt.parse("USER-AGENT: *\nDISALLOW: /a/\n").isAllowed(US, "/a/b")).isFalse();
    }

    @Test
    void readsACrawlDelay() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nCrawl-delay: 5\nDisallow: /x\n");

        assertThat(robots.crawlDelay(US)).contains(Duration.ofSeconds(5));
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow: /x\n").crawlDelay(US)).isEmpty();
    }

    @Test
    void noRobotsFileMeansAllowedAndAnUnreadableOneMeansNothingIs() {
        assertThat(RobotsTxt.allowEverything().isAllowed(US, "/anything")).isTrue();
        assertThat(RobotsTxt.denyEverything().isAllowed(US, "/anything")).isFalse();
    }

    @Test
    void anEmptyFileAllowsEverything() {
        assertThat(RobotsTxt.parse("").isAllowed(US, "/anything")).isTrue();
    }
}
