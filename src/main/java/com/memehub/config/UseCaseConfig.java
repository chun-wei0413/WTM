package com.memehub.config;

import com.memehub.adapter.scheduling.GenerationProperties;
import com.memehub.application.auth.BootstrapAdminHandler;
import com.memehub.application.auth.LoginHandler;
import com.memehub.application.generation.GenerationQueueHandler;
import com.memehub.application.generation.GetGenerationHandler;
import com.memehub.application.generation.KeepMemeHandler;
import com.memehub.application.generation.RunGenerationHandler;
import com.memehub.application.generation.SubmitGenerationHandler;
import com.memehub.application.port.out.EmbeddingPort;
import com.memehub.application.port.out.GenerationJobStore;
import com.memehub.application.port.out.GenerationJobStore.QuotaLimits;
import com.memehub.application.port.out.GenerationReadPort;
import com.memehub.application.port.out.MemeAssistantPort;
import com.memehub.application.port.out.MemeRendererPort;
import com.memehub.application.port.out.MemeRepository;
import com.memehub.application.port.out.ImageInspectorPort;
import com.memehub.application.port.out.ObjectStoragePort;
import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.SearchIndexPort;
import com.memehub.application.port.out.TemplateReadPort;
import com.memehub.application.port.out.TemplateRepository;
import com.memehub.application.port.out.TemplateSearchPort;
import com.memehub.application.port.out.TokenIssuer;
import com.memehub.application.port.out.UserRepository;
import com.memehub.application.template.command.ApproveTemplateHandler;
import com.memehub.application.template.command.DefineSlotHandler;
import com.memehub.application.template.command.DraftTemplateHandler;
import com.memehub.application.template.command.RedefineSlotHandler;
import com.memehub.application.template.command.RemoveSlotHandler;
import com.memehub.application.template.command.RetireTemplateHandler;
import com.memehub.application.template.command.ReviseProfileHandler;
import com.memehub.application.template.index.SyncSearchIndexHandler;
import com.memehub.application.template.query.GetTemplateHandler;
import com.memehub.application.template.query.ListTemplatesHandler;
import com.memehub.application.template.search.SearchTemplatesHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free application handlers into the Spring context.
 */
@Configuration
class UseCaseConfig {

    @Bean
    LoginHandler loginHandler(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        return new LoginHandler(users, hasher, tokens);
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
}
