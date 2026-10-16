package com.wtm.config;

import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase;
import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.feedback.adapter.in.scheduling.ReportProperties;
import com.wtm.feedback.adapter.in.scheduling.ReviewProperties;
import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.usecase.port.out.report.ReportCaseRepository;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort;
import com.wtm.feedback.usecase.port.out.report.ReporterPort;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.feedback.usecase.service.ListReportCasesService;
import com.wtm.feedback.usecase.service.ReportJudge;
import com.wtm.feedback.usecase.service.ResolveReportsService;
import com.wtm.feedback.usecase.service.ReviewQueueService;
import com.wtm.feedback.usecase.service.RunReviewService;
import com.wtm.feedback.usecase.service.SubmitReportService;
import com.wtm.shared.port.ObjectStoragePort;
import com.wtm.shared.port.VisionTaggerPort;
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
    ReportJudge reportJudge(ReporterPort reporters, ReportPolicy policy) {
        return new ReportJudge(reporters, policy);
    }

    @Bean
    SubmitReportService submitReportService(ReportCaseRepository cases, ReporterPort reporters,
                                            GetTemplateUseCase templates, ReviewPort reviews, ReportJudge judge,
                                            ResolveReportsService resolver) {
        return new SubmitReportService(cases, reporters, templates, reviews, judge, resolver, Clock.systemUTC());
    }

    @Bean
    RunReviewService runReviewService(GetTemplateUseCase templates, ObjectStoragePort storage,
                                      VisionTaggerPort tagger, ReportCaseRepository cases, ReviewPort reviews,
                                      ReportJudge judge, ResolveReportsService resolver,
                                      ReviewProperties properties) {
        return new RunReviewService(templates, storage, tagger, cases, reviews, judge, resolver,
                properties.maxAttempts());
    }

    @Bean
    ReviewQueueService reviewQueueService(ReviewPort reviews, ReportPolicy policy) {
        return new ReviewQueueService(reviews, policy);
    }

    @Bean
    ListReportCasesService listReportCasesService(ReportViewPort reports, ReviewPort reviews, GetTemplateUseCase templates,
                                                  ObjectStoragePort storage, RestoreProfileUseCase restore,
                                                  ReportJudge judge) {
        return new ListReportCasesService(reports, reviews, templates, storage, restore, judge, Clock.systemUTC());
    }

    @Bean
    ResolveReportsService resolveReportsService(ReportCaseRepository cases, ReviewPort reviews,
                                                ReviseProfileFromProposalUseCase revise, RestoreProfileUseCase restore) {
        return new ResolveReportsService(cases, reviews, revise, restore, Clock.systemUTC());
    }
}
