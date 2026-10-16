package com.wtm.config;

import com.wtm.curation.adapter.in.scheduling.TaggingProperties;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.curation.usecase.port.out.vision.VisionTaggerPort;
import com.wtm.feedback.adapter.in.scheduling.ReportProperties;
import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.usecase.port.out.report.ReportPort;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.feedback.usecase.service.ListReportCasesService;
import com.wtm.feedback.usecase.service.ReportJudge;
import com.wtm.feedback.usecase.service.ResolveReportsService;
import com.wtm.feedback.usecase.service.ReviewQueueService;
import com.wtm.feedback.usecase.service.RunReviewService;
import com.wtm.feedback.usecase.service.SubmitReportService;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free services of Feedback into the Spring context.
 */
@Configuration
class FeedbackConfig {

    @Bean
    ReportPolicy reportPolicy(ReportProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    ReportJudge reportJudge(ReportPort reports, ReportPolicy policy) {
        return new ReportJudge(reports, policy);
    }

    @Bean
    SubmitReportService submitReportService(ReportPort reports, ReviewPort reviews, ReportJudge judge,
                                            ResolveReportsService resolver) {
        return new SubmitReportService(reports, reviews, judge, resolver, Clock.systemUTC());
    }

    @Bean
    RunReviewService runReviewService(TemplateReadPort templates, ObjectStoragePort storage,
                                      VisionTaggerPort tagger, ReportPort reports, ReviewPort reviews,
                                      ReportJudge judge, ResolveReportsService resolver,
                                      TaggingProperties properties) {
        return new RunReviewService(templates, storage, tagger, reports, reviews, judge, resolver,
                properties.maxAttempts());
    }

    @Bean
    ReviewQueueService reviewQueueService(ReviewPort reviews, ReportPolicy policy) {
        return new ReviewQueueService(reviews, policy);
    }

    @Bean
    ListReportCasesService listReportCasesService(ReportPort reports, ReviewPort reviews, TemplateReadPort templates,
                                                  ObjectStoragePort storage, ProfileHistoryPort history,
                                                  ReportJudge judge) {
        return new ListReportCasesService(reports, reviews, templates, storage, history, judge, Clock.systemUTC());
    }

    @Bean
    ResolveReportsService resolveReportsService(ReportPort reports, ReviewPort reviews,
                                                TemplateRepository templates, ProfileHistoryPort history) {
        return new ResolveReportsService(reports, reviews, templates, history, Clock.systemUTC());
    }
}
