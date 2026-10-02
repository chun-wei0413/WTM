package com.wtm.config;

import com.wtm.adapter.scheduling.GenerationProperties;
import com.wtm.adapter.scheduling.TaggingProperties;
import com.wtm.adapter.security.SecurityProperties;
import com.wtm.application.auth.BootstrapAdminHandler;
import com.wtm.application.auth.LoginHandler;
import com.wtm.application.collection.ApplyTagsHandler;
import com.wtm.application.library.FavoritesHandler;
import com.wtm.application.library.GetLibraryImageHandler;
import com.wtm.application.library.RandomMemesHandler;
import com.wtm.application.library.SearchHistoryHandler;
import com.wtm.application.report.ListReportCasesHandler;
import com.wtm.application.report.ResolveReportsHandler;
import com.wtm.application.report.ReviewQueueHandler;
import com.wtm.application.report.RunReviewHandler;
import com.wtm.application.report.SubmitReportHandler;
import com.wtm.application.collection.GetLibraryStatsHandler;
import com.wtm.application.collection.ImportFromUrlHandler;
import com.wtm.application.collection.ListCollectionRunsHandler;
import com.wtm.application.collection.RunCollectionHandler;
import com.wtm.application.collection.StartCollectionHandler;
import com.wtm.application.collection.ImportUploadsHandler;
import com.wtm.application.collection.IngestMemeHandler;
import com.wtm.application.collection.TagTemplateHandler;
import com.wtm.application.collection.TaggingQueueHandler;
import com.wtm.application.auth.LoginPolicy;
import com.wtm.application.auth.RegisterUserHandler;
import com.wtm.application.auth.RegistrationPolicy;
import com.wtm.application.generation.GenerationQueueHandler;
import com.wtm.application.generation.GetGenerationHandler;
import com.wtm.application.generation.GetMemeImageHandler;
import com.wtm.application.generation.KeepMemeHandler;
import com.wtm.application.generation.ListMyMemesHandler;
import com.wtm.application.generation.RunGenerationHandler;
import com.wtm.application.generation.SubmitGenerationHandler;
import com.wtm.application.port.out.EmbeddingPort;
import com.wtm.application.port.out.FavoritePort;
import com.wtm.application.port.out.LibraryBrowsePort;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.SearchLogPort;
import com.wtm.application.port.out.GenerationJobStore;
import com.wtm.application.port.out.GenerationJobStore.QuotaLimits;
import com.wtm.application.port.out.GenerationReadPort;
import com.wtm.application.port.out.MemeAssistantPort;
import com.wtm.application.port.out.MemeReadPort;
import com.wtm.application.port.out.MemeRendererPort;
import com.wtm.application.port.out.MemeRepository;
import com.wtm.application.port.out.CollectionRunPort;
import com.wtm.application.port.out.ImageFingerprintPort;
import com.wtm.application.port.out.ImageInspectorPort;
import com.wtm.application.port.out.LibraryPort;
import com.wtm.application.port.out.MemeSourcePort;
import com.wtm.application.port.out.RemoteFetchPort;
import com.wtm.application.port.out.TaggingQueuePort;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.PasswordHasher;
import com.wtm.application.port.out.RateLimiterPort;
import com.wtm.application.port.out.SearchIndexPort;
import com.wtm.application.port.out.TemplateReadPort;
import com.wtm.application.port.out.TemplateRepository;
import com.wtm.application.port.out.TemplateSearchPort;
import com.wtm.application.port.out.TokenIssuer;
import com.wtm.application.port.out.UserRepository;
import com.wtm.application.template.command.ApproveTemplateHandler;
import com.wtm.application.template.command.DefineSlotHandler;
import com.wtm.application.template.command.DraftTemplateHandler;
import com.wtm.application.template.command.RedefineSlotHandler;
import com.wtm.application.template.command.RemoveSlotHandler;
import com.wtm.application.template.command.RetireTemplateHandler;
import com.wtm.application.template.command.ReviseProfileHandler;
import com.wtm.application.template.index.SyncSearchIndexHandler;
import com.wtm.application.template.query.GetTemplateHandler;
import com.wtm.application.template.query.ListTemplatesHandler;
import com.wtm.application.template.search.SearchTemplatesHandler;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free application handlers into the Spring context.
 */
@Configuration
class UseCaseConfig {

    @Bean
    LoginHandler loginHandler(UserRepository users, PasswordHasher hasher, TokenIssuer tokens,
                              RateLimiterPort limiter, SecurityProperties security) {
        var throttling = security.throttling();
        return new LoginHandler(users, hasher, tokens, limiter, new LoginPolicy(
                throttling.loginFailuresPerAccount(), throttling.loginFailuresPerAddress(), throttling.loginWindow()));
    }

    @Bean
    RegisterUserHandler registerUserHandler(UserRepository users, PasswordHasher hasher,
                                            RateLimiterPort limiter, SecurityProperties security) {
        var throttling = security.throttling();
        return new RegisterUserHandler(users, hasher, limiter, new RegistrationPolicy(
                security.registrationEnabled(), throttling.registrationsPerAddress(), throttling.registrationWindow()));
    }

    @Bean
    BootstrapAdminHandler bootstrapAdminHandler(UserRepository users, PasswordHasher hasher) {
        return new BootstrapAdminHandler(users, hasher);
    }

    @Bean
    DraftTemplateHandler draftTemplateHandler(ImageInspectorPort inspector, ObjectStoragePort storage,
                                              TemplateRepository templates) {
        return new DraftTemplateHandler(inspector, storage, templates);
    }

    @Bean
    DefineSlotHandler defineSlotHandler(TemplateRepository templates) {
        return new DefineSlotHandler(templates);
    }

    @Bean
    RedefineSlotHandler redefineSlotHandler(TemplateRepository templates) {
        return new RedefineSlotHandler(templates);
    }

    @Bean
    RemoveSlotHandler removeSlotHandler(TemplateRepository templates) {
        return new RemoveSlotHandler(templates);
    }

    @Bean
    ReviseProfileHandler reviseProfileHandler(TemplateRepository templates) {
        return new ReviseProfileHandler(templates);
    }

    @Bean
    ApproveTemplateHandler approveTemplateHandler(TemplateRepository templates) {
        return new ApproveTemplateHandler(templates);
    }

    @Bean
    RetireTemplateHandler retireTemplateHandler(TemplateRepository templates) {
        return new RetireTemplateHandler(templates);
    }

    @Bean
    GetTemplateHandler getTemplateHandler(TemplateReadPort reads, ObjectStoragePort storage) {
        return new GetTemplateHandler(reads, storage);
    }

    @Bean
    ListTemplatesHandler listTemplatesHandler(TemplateReadPort reads, ObjectStoragePort storage) {
        return new ListTemplatesHandler(reads, storage);
    }

    @Bean
    SyncSearchIndexHandler syncSearchIndexHandler(SearchIndexPort index, EmbeddingPort embeddings) {
        return new SyncSearchIndexHandler(index, embeddings);
    }

    @Bean
    SearchTemplatesHandler searchTemplatesHandler(TemplateSearchPort search, EmbeddingPort embeddings,
                                                  ObjectStoragePort storage) {
        return new SearchTemplatesHandler(search, embeddings, storage);
    }

    @Bean
    RandomMemesHandler randomMemesHandler(LibraryBrowsePort library, ObjectStoragePort storage) {
        return new RandomMemesHandler(library, storage);
    }

    @Bean
    FavoritesHandler favoritesHandler(FavoritePort favorites, ObjectStoragePort storage) {
        return new FavoritesHandler(favorites, storage);
    }

    @Bean
    GetLibraryImageHandler getLibraryImageHandler(LibraryBrowsePort library, ObjectStoragePort storage) {
        return new GetLibraryImageHandler(library, storage);
    }

    @Bean
    SearchHistoryHandler searchHistoryHandler(SearchLogPort log) {
        return new SearchHistoryHandler(log, Clock.systemUTC());
    }

    @Bean
    SubmitGenerationHandler submitGenerationHandler(GenerationJobStore jobs, GenerationProperties properties) {
        return new SubmitGenerationHandler(jobs,
                new QuotaLimits(properties.maxActivePerUser(), properties.maxPerDayPerUser()));
    }

    @Bean
    GetGenerationHandler getGenerationHandler(GenerationReadPort reads, ObjectStoragePort storage) {
        return new GetGenerationHandler(reads, storage);
    }

    @Bean
    KeepMemeHandler keepMemeHandler(MemeRepository memes) {
        return new KeepMemeHandler(memes);
    }

    @Bean
    GetMemeImageHandler getMemeImageHandler(MemeRepository memes, ObjectStoragePort storage) {
        return new GetMemeImageHandler(memes, storage);
    }

    @Bean
    ListMyMemesHandler listMyMemesHandler(MemeReadPort reads, ObjectStoragePort storage) {
        return new ListMyMemesHandler(reads, storage);
    }

    @Bean
    GenerationQueueHandler generationQueueHandler(GenerationJobStore jobs) {
        return new GenerationQueueHandler(jobs);
    }

    @Bean
    RunGenerationHandler runGenerationHandler(SearchTemplatesHandler search, TemplateReadPort templates,
                                              MemeAssistantPort assistant, MemeRendererPort renderer,
                                              ObjectStoragePort storage, MemeRepository memes,
                                              GenerationJobStore jobs, GenerationProperties properties) {
        return new RunGenerationHandler(search, templates, assistant, renderer, storage, memes, jobs,
                properties.candidates());
    }

    @Bean
    IngestMemeHandler ingestMemeHandler(ImageInspectorPort inspector, ImageFingerprintPort fingerprints,
                                        LibraryPort library, ObjectStoragePort storage) {
        return new IngestMemeHandler(inspector, fingerprints, library, storage);
    }

    @Bean
    ImportUploadsHandler importUploadsHandler(IngestMemeHandler ingest) {
        return new ImportUploadsHandler(ingest);
    }

    @Bean
    GetLibraryStatsHandler getLibraryStatsHandler(LibraryPort library) {
        return new GetLibraryStatsHandler(library);
    }

    @Bean
    ApplyTagsHandler applyTagsHandler(TemplateRepository templates) {
        return new ApplyTagsHandler(templates);
    }

    @Bean
    TagTemplateHandler tagTemplateHandler(TemplateReadPort reads, ObjectStoragePort storage,
                                          VisionTaggerPort tagger, ApplyTagsHandler apply,
                                          TaggingQueuePort queue, TaggingProperties properties) {
        return new TagTemplateHandler(reads, storage, tagger, apply, queue, properties.maxAttempts());
    }

    @Bean
    TaggingQueueHandler taggingQueueHandler(TaggingQueuePort queue) {
        return new TaggingQueueHandler(queue);
    }

    @Bean
    SubmitReportHandler submitReportHandler(ReportPort reports, ReviewPort reviews) {
        return new SubmitReportHandler(reports, reviews);
    }

    @Bean
    RunReviewHandler runReviewHandler(TemplateReadPort templates, ObjectStoragePort storage,
                                      VisionTaggerPort tagger, ReportPort reports, ReviewPort reviews,
                                      TaggingProperties properties) {
        return new RunReviewHandler(templates, storage, tagger, reports, reviews, properties.maxAttempts());
    }

    @Bean
    ReviewQueueHandler reviewQueueHandler(ReviewPort reviews) {
        return new ReviewQueueHandler(reviews);
    }

    @Bean
    ListReportCasesHandler listReportCasesHandler(ReportPort reports, ReviewPort reviews, TemplateReadPort templates,
                                                  ObjectStoragePort storage) {
        return new ListReportCasesHandler(reports, reviews, templates, storage);
    }

    @Bean
    ResolveReportsHandler resolveReportsHandler(ReportPort reports, ReviewPort reviews,
                                                TemplateRepository templates) {
        return new ResolveReportsHandler(reports, reviews, templates);
    }

    @Bean
    StartCollectionHandler startCollectionHandler(List<MemeSourcePort> sources, CollectionRunPort runs) {
        return new StartCollectionHandler(sources, runs);
    }

    @Bean
    RunCollectionHandler runCollectionHandler(RemoteFetchPort fetcher, IngestMemeHandler ingest,
                                              CollectionRunPort runs) {
        return new RunCollectionHandler(fetcher, ingest, runs);
    }

    @Bean
    ImportFromUrlHandler importFromUrlHandler(RemoteFetchPort fetcher, IngestMemeHandler ingest) {
        return new ImportFromUrlHandler(fetcher, ingest);
    }

    @Bean
    ListCollectionRunsHandler listCollectionRunsHandler(CollectionRunPort runs) {
        return new ListCollectionRunsHandler(runs);
    }
}
