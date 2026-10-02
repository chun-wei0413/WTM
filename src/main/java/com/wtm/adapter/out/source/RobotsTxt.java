package com.wtm.adapter.out.source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parsed robots.txt, read the way RFC 9309 describes: the rules of the most specific matching
 * group apply; within them the longest matching pattern wins and an Allow beats a Disallow of the
 * same length; {@code *} matches anything and a trailing {@code $} anchors the end.
 */
final class RobotsTxt {

    private record Rule(boolean allow, Pattern pattern, int length) {
    }

    private record Group(List<String> agents, List<Rule> rules, Duration crawlDelay) {
    }

    private final List<Group> groups;
    private final boolean allowAll;
    private final boolean denyAll;

    private RobotsTxt(List<Group> groups, boolean allowAll, boolean denyAll) {
        this.groups = groups;
        this.allowAll = allowAll;
        this.denyAll = denyAll;
    }

    /** What to assume when the site has no robots.txt (a 4xx answer): everything is allowed. */
    static RobotsTxt allowEverything() {
        return new RobotsTxt(List.of(), true, false);
    }

    /** What to assume when robots.txt cannot be read (a server error): nothing is allowed. */
    static RobotsTxt denyEverything() {
        return new RobotsTxt(List.of(), false, true);
    }

    static RobotsTxt parse(String text) {
        List<Group> groups = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        Duration delay = null;
        boolean inRules = false;

        for (String rawLine : text.split("\\R")) {
            String line = stripComment(rawLine).strip();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String field = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            switch (field) {
                case "user-agent" -> {
                    if (inRules) {                                   // a new group starts
                        groups.add(new Group(agents, rules, delay));
                        agents = new ArrayList<>();
                        rules = new ArrayList<>();
                        delay = null;
                        inRules = false;
                    }
                    agents.add(value.toLowerCase(Locale.ROOT));
                }
                case "allow", "disallow" -> {
                    inRules = true;
                    if (!value.isEmpty()) {                          // an empty Disallow allows everything
                        rules.add(new Rule(field.equals("allow"), compile(value), value.length()));
                    }
                }
                case "crawl-delay" -> {
                    inRules = true;
                    try {
                        delay = Duration.ofMillis((long) (Double.parseDouble(value) * 1000));
                    } catch (NumberFormatException ignored) {
                        // not a number: no delay requested
                    }
                }
                default -> {
                    // Sitemap and unknown fields do not affect what may be fetched.
                }
            }
        }
        if (!agents.isEmpty()) {
            groups.add(new Group(agents, rules, delay));
        }
        return new RobotsTxt(groups, false, false);
    }

    boolean isAllowed(String productToken, String path) {
        if (allowAll) {
            return true;
        }
        if (denyAll) {
            return false;
        }
        Optional<Group> group = groupFor(productToken);
        if (group.isEmpty()) {
            return true;
        }
        Rule best = null;
        for (Rule rule : group.get().rules()) {
            if (rule.pattern().matcher(path).find()) {
                if (best == null || rule.length() > best.length() || (rule.length() == best.length() && rule.allow())) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow();
    }

    /** How long the site asks to wait between requests, if it says. */
    Optional<Duration> crawlDelay(String productToken) {
        return groupFor(productToken).map(Group::crawlDelay);
    }

    private Optional<Group> groupFor(String productToken) {
        String product = productToken.toLowerCase(Locale.ROOT);
        Group specific = null;
        int specificLength = -1;
        Group wildcard = null;
        for (Group group : groups) {
            for (String agent : group.agents()) {
                if (agent.equals("*")) {
                    wildcard = wildcard == null ? group : wildcard;
                } else if (product.contains(agent) && agent.length() > specificLength) {
                    specific = group;
                    specificLength = agent.length();
                }
            }
        }
        return Optional.ofNullable(specific != null ? specific : wildcard);
    }

    private static Pattern compile(String robotsPattern) {
        boolean anchored = robotsPattern.endsWith("$");
        String body = anchored ? robotsPattern.substring(0, robotsPattern.length() - 1) : robotsPattern;
        StringBuilder regex = new StringBuilder("^");
        for (char c : body.toCharArray()) {
            regex.append(c == '*' ? ".*" : Pattern.quote(String.valueOf(c)));
        }
        if (anchored) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString());
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }
}
