package com.wtm.config;

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.discovery.usecase.port.out.embedding.EmbeddingPort;
import com.wtm.discovery.usecase.port.out.explainer.MemeExplainerPort;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteListPort;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteRepository;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort;
import com.wtm.discovery.usecase.port.out.search.KeywordSearchPort;
import com.wtm.discovery.usecase.port.out.search.SearchIndexPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import com.wtm.discovery.usecase.port.out.searchlog.SearchLogPort;
import com.wtm.discovery.usecase.service.FavoritesService;
import com.wtm.discovery.usecase.service.GetLibraryImageService;
import com.wtm.discovery.usecase.service.PickMemeService;
import com.wtm.discovery.usecase.service.RandomMemesService;
import com.wtm.discovery.usecase.service.SearchHistoryService;
import com.wtm.discovery.usecase.service.SearchTemplatesService;
import com.wtm.discovery.usecase.service.SyncSearchIndexService;
import com.wtm.shared.RateLimiterPort;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free services of Discovery into the Spring context.
 */
@Configuration
class DiscoveryConfig {

    @Bean
    SyncSearchIndexService syncSearchIndexService(SearchIndexPort index, EmbeddingPort embeddings) {
        return new SyncSearchIndexService(index, embeddings);
    }

    @Bean
    SearchTemplatesService searchTemplatesService(TemplateSearchPort search, KeywordSearchPort keywords, EmbeddingPort embeddings,
                                                  ObjectStoragePort storage, SearchProperties properties) {
        return new SearchTemplatesService(search, keywords, embeddings, storage, properties.denseWeight());
    }

    @Bean
    PickMemeService pickMemeService(SearchTemplatesService search, MemeExplainerPort explainer, RateLimiterPort limiter) {
        return new PickMemeService(search, explainer, limiter);
    }

    @Bean
    RandomMemesService randomMemesService(LibraryBrowsePort library, ObjectStoragePort storage) {
        return new RandomMemesService(library, storage);
    }

    @Bean
    FavoritesService favoritesService(FavoriteRepository favorites, FavoriteListPort shelf,
                                      GetTemplateUseCase templates, ObjectStoragePort storage) {
        return new FavoritesService(favorites, shelf, templates, storage, Clock.systemUTC());
    }

    @Bean
    GetLibraryImageService getLibraryImageService(LibraryBrowsePort library, ObjectStoragePort storage) {
        return new GetLibraryImageService(library, storage);
    }

    @Bean
    SearchHistoryService searchHistoryService(SearchLogPort log) {
        return new SearchHistoryService(log, Clock.systemUTC());
    }
}
