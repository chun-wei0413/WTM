package com.wtm.adapter.scheduling;

import com.wtm.application.report.ReportPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits that keep reports from keeping the vision model busy, and when the rules may act on their own.
 * See {@link ReportPolicy} for what each number means.
 */
@ConfigurationProperties("wtm.reports")
public record ReportProperties(
        @DefaultValue("10") int dailyReportsPerUser,
        @DefaultValue("50") int dailyAnalyses,
        @DefaultValue("PT24H") Duration cooldownPerMeme,
        @DefaultValue("2") int analyzeAtWeight,
        @DefaultValue("3") int autoApplyAtWeight,
        @DefaultValue("2") int trustedWeight,
        @DefaultValue("5") int lowTrustDismissals) {

    public ReportPolicy toPolicy() {
        return new ReportPolicy(dailyReportsPerUser, dailyAnalyses, cooldownPerMeme, analyzeAtWeight,
                autoApplyAtWeight, trustedWeight, lowTrustDismissals);
    }
}
