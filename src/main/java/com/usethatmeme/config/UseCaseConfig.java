package com.usethatmeme.config;

import com.usethatmeme.adapter.scheduling.GenerationProperties;
import com.usethatmeme.adapter.scheduling.TaggingProperties;
import com.usethatmeme.adapter.security.SecurityProperties;
import com.usethatmeme.application.auth.BootstrapAdminHandler;
import com.usethatmeme.application.auth.LoginHandler;
import com.usethatmeme.application.collection.ApplyTagsHandler;
import com.usethatmeme.application.collection.GetLibraryStatsHandler;
import com.usethatmeme.application.collection.ImportFromUrlHandler;
import com.usethatmeme.application.collection.ListCollectionRunsHandler;
import com.usethatmeme.application.collection.RunCollectionHandler;
import com.usethatmeme.application.collection.StartCollectionHandler;
import com.usethatmeme.application.collection.ImportUploadsHandler;
import com.usethatmeme.application.collection.IngestMemeHandler;
import com.usethatmeme.application.collection.TagTemplateHandler;
import com.usethatmeme.application.collection.TaggingQueueHandler;
import com.usethatmeme.application.auth.LoginPolicy;
import com.usethatmeme.application.auth.RegisterUserHandler;
import com.usethatmeme.application.auth.RegistrationPolicy;
import com.usethatmeme.application.generation.GenerationQueueHandler;
import com.usethatmeme.application.generation.GetGenerationHandler;
import com.usethatmeme.application.generation.GetMemeImageHandler;
import com.usethatmeme.application.generation.KeepMemeHandler;
import com.usethatmeme.application.generation.ListMyMemesHandler;
import com.usethatmeme.application.generation.RunGenerationHandler;
import com.usethatmeme.application.generation.SubmitGenerationHandler;
import com.usethatmeme.application.port.out.EmbeddingPort;
import com.usethatmeme.application.port.out.GenerationJobStore;
import com.usethatmeme.application.port.out.GenerationJobStore.QuotaLimits;
import com.usethatmeme.application.port.out.GenerationReadPort;
import com.usethatmeme.application.port.out.MemeAssistantPort;
import com.usethatmeme.application.port.out.MemeReadPort;
import com.usethatmeme.application.port.out.MemeRendererPort;
import com.usethatmeme.application.port.out.MemeRepository;
import com.usethatmeme.application.port.out.CollectionRunPort;
import com.usethatmeme.application.port.out.ImageFingerprintPort;
import com.usethatmeme.application.port.out.ImageInspectorPort;
import com.usethatmeme.application.port.out.LibraryPort;
import com.usethatmeme.application.port.out.MemeSourcePort;
import com.usethatmeme.application.port.out.RemoteFetchPort;
import com.usethatmeme.application.port.out.TaggingQueuePort;
import com.usethatmeme.application.port.out.VisionTaggerPort;
import com.usethatmeme.application.port.out.ObjectStoragePort;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.RateLimiterPort;
import com.usethatmeme.application.port.out.SearchIndexPort;
import com.usethatmeme.application.port.out.TemplateReadPort;
import com.usethatmeme.application.port.out.TemplateRepository;
import com.usethatmeme.application.port.out.TemplateSearchPort;
import com.usethatmeme.application.port.out.TokenIssuer;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.application.template.command.ApproveTemplateHandler;
import com.usethatmeme.application.template.command.DefineSlotHandler;
import com.usethatmeme.application.template.command.DraftTemplateHandler;
import com.usethatmeme.application.template.command.RedefineSlotHandler;
import com.usethatmeme.application.template.command.RemoveSlotHandler;
import com.usethatmeme.application.template.command.RetireTemplateHandler;
import com.usethatmeme.application.template.command.ReviseProfileHandler;
import com.usethatmeme.application.template.index.SyncSearchIndexHandler;
import com.usethatmeme.application.template.query.GetTemplateHandler;
import com.usethatmeme.application.template.query.ListTemplatesHandler;
import com.usethatmeme.application.template.search.SearchTemplatesHandler;
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
