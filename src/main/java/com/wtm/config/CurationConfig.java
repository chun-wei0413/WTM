package com.wtm.config;

import com.wtm.curation.adapter.in.scheduling.TaggingProperties;
import com.wtm.curation.usecase.port.out.fetch.RemoteFetchPort;
import com.wtm.curation.usecase.port.out.image.ImageFingerprintPort;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort;
import com.wtm.curation.usecase.port.out.library.LibraryPort;
import com.wtm.curation.usecase.port.out.tagging.TaggingQueuePort;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.curation.usecase.service.ApplyTagsService;
import com.wtm.curation.usecase.service.ApproveTemplateService;
import com.wtm.curation.usecase.service.DefineSlotService;
import com.wtm.curation.usecase.service.DraftTemplateService;
import com.wtm.curation.usecase.service.GetLibraryStatsService;
import com.wtm.curation.usecase.service.GetTemplateService;
import com.wtm.curation.usecase.service.ImportFromUrlService;
import com.wtm.curation.usecase.service.ImportUploadsService;
import com.wtm.curation.usecase.service.IngestMemeService;
import com.wtm.curation.usecase.service.ListTemplatesService;
import com.wtm.curation.usecase.service.RedefineSlotService;
import com.wtm.curation.usecase.service.RemoveSlotService;
import com.wtm.curation.usecase.service.RestoreProfileService;
import com.wtm.curation.usecase.service.RetireTemplateService;
import com.wtm.curation.usecase.service.ReviseProfileFromProposalService;
import com.wtm.curation.usecase.service.ReviseProfileService;
import com.wtm.curation.usecase.service.TagTemplateService;
import com.wtm.curation.usecase.service.TaggingQueueService;
import com.wtm.shared.port.ObjectStoragePort;
import com.wtm.shared.port.VisionTaggerPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free services of Curation into the Spring context.
 */
@Configuration
class CurationConfig {

    @Bean
    DraftTemplateService draftTemplateService(ImageInspectorPort inspector, ObjectStoragePort storage,
                                              TemplateRepository templates) {
        return new DraftTemplateService(inspector, storage, templates);
    }

    @Bean
    DefineSlotService defineSlotService(TemplateRepository templates) {
        return new DefineSlotService(templates);
    }

    @Bean
    RedefineSlotService redefineSlotService(TemplateRepository templates) {
        return new RedefineSlotService(templates);
    }

    @Bean
    RemoveSlotService removeSlotService(TemplateRepository templates) {
        return new RemoveSlotService(templates);
    }

    @Bean
    ReviseProfileService reviseProfileService(TemplateRepository templates) {
        return new ReviseProfileService(templates);
    }

    @Bean
    ApproveTemplateService approveTemplateService(TemplateRepository templates) {
        return new ApproveTemplateService(templates);
    }

    @Bean
    RetireTemplateService retireTemplateService(TemplateRepository templates) {
        return new RetireTemplateService(templates);
    }

    @Bean
    GetTemplateService getTemplateService(TemplateReadPort reads, ObjectStoragePort storage) {
        return new GetTemplateService(reads, storage);
    }

    @Bean
    ListTemplatesService listTemplatesService(TemplateReadPort reads, ObjectStoragePort storage) {
        return new ListTemplatesService(reads, storage);
    }

    @Bean
    IngestMemeService ingestMemeService(ImageInspectorPort inspector, ImageFingerprintPort fingerprints,
                                        LibraryPort library, ObjectStoragePort storage) {
        return new IngestMemeService(inspector, fingerprints, library, storage);
    }

    @Bean
    ImportUploadsService importUploadsService(IngestMemeService ingest) {
        return new ImportUploadsService(ingest);
    }

    @Bean
    GetLibraryStatsService getLibraryStatsService(LibraryPort library) {
        return new GetLibraryStatsService(library);
    }

    @Bean
    ApplyTagsService applyTagsService(TemplateRepository templates) {
        return new ApplyTagsService(templates);
    }

    @Bean
    TagTemplateService tagTemplateService(TemplateReadPort reads, LibraryPort library, ObjectStoragePort storage,
                                          VisionTaggerPort tagger, ApplyTagsService apply,
                                          TaggingQueuePort queue, TaggingProperties properties) {
        return new TagTemplateService(reads, library, storage, tagger, apply, queue, properties.maxAttempts());
    }

    @Bean
    TaggingQueueService taggingQueueService(TaggingQueuePort queue) {
        return new TaggingQueueService(queue);
    }

    @Bean
    ImportFromUrlService importFromUrlService(RemoteFetchPort fetcher, IngestMemeService ingest) {
        return new ImportFromUrlService(fetcher, ingest);
    }

    @Bean
    ReviseProfileFromProposalService reviseProfileFromProposalService(TemplateRepository templates) {
        return new ReviseProfileFromProposalService(templates, Clock.systemUTC());
    }

    @Bean
    RestoreProfileService restoreProfileService(TemplateRepository templates) {
        return new RestoreProfileService(templates, Clock.systemUTC());
    }
}
