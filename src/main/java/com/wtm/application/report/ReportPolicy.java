package com.wtm.application.report;

import java.time.Duration;

/**
 * How much the system trusts reports, and how much model time it is willing to spend on them. Every number
 * is a limit that keeps a flood of reports, real or malicious, from keeping the vision model busy.
 *
 * @param dailyReportsPerUser how many different memes one person may report in 24 hours
 * @param dailyAnalyses how many times the vision model may look at a reported meme in 24 hours, for the whole site
 * @param cooldownPerMeme how long a meme is left alone after the model has looked at it
 * @param analyzeAtWeight reporters' combined weight needed before the model is asked to look (an administrator
 *                        can always ask by hand)
 * @param autoApplyAtWeight combined weight at which the model's proposal is adopted without an administrator,
 *                          and below which a "nothing wrong" answer closes the reports by itself
 * @param trustedWeight what a reporter counts for once earlier reports of theirs were adopted
 * @param lowTrustDismissals reporters whose reports were set aside this often, and never adopted, count for nothing
 */
public record ReportPolicy(int dailyReportsPerUser, int dailyAnalyses, Duration cooldownPerMeme, int analyzeAtWeight,
                           int autoApplyAtWeight, int trustedWeight, int lowTrustDismissals) {

    /** What the system has learned about a reporter from how their earlier reports ended. */
    public record Standing(boolean administrator, int adopted, int setAside) {
    }

    public static ReportPolicy standard() {
        return new ReportPolicy(10, 50, Duration.ofHours(24), 2, 3, 2, 5);
    }

    /** How much one reporter's word counts: 0 for a habitual false reporter, more for a proven one. */
    public int weightOf(Standing standing) {
        if (standing.administrator()) {
            return analyzeAtWeight;   // enough to get the model's look, never enough to change anything alone
        }
        if (standing.adopted() == 0 && standing.setAside() >= lowTrustDismissals) {
            return 0;
        }
        if (standing.adopted() >= 2 && standing.adopted() > standing.setAside()) {
            return trustedWeight;
        }
        return 1;
    }
}
