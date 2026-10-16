package com.wtm.discovery.usecase.service;

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.discovery.entity.Favorite;
import com.wtm.discovery.usecase.port.in.command.FavoritesUseCase;
import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteListPort;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteRepository;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.UserId;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's own shortlist of library memes.
 */
public class FavoritesService implements FavoritesUseCase {

    private final FavoriteRepository favorites;
    private final FavoriteListPort shelf;
    private final GetTemplateUseCase templates;
    private final ObjectStoragePort storage;
    private final Clock clock;

    public FavoritesService(FavoriteRepository favorites, FavoriteListPort shelf, GetTemplateUseCase templates,
                            ObjectStoragePort storage, Clock clock) {
        this.favorites = favorites;
        this.shelf = shelf;
        this.templates = templates;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public List<LibraryItem> list(UserId userId) {
        return shelf.list(userId).stream().map(card -> LibraryItems.of(card, storage)).toList();
    }

    /**
     * Adding a favorite twice changes nothing.
     *
     * @throws TemplateNotFoundException when there is no published meme with that id
     */
    @Transactional
    @Override
    public void add(UserId userId, UUID templateId) {
        if (!"APPROVED".equals(templates.handle(templateId).status())) {
            throw new TemplateNotFoundException(new TemplateId(templateId));
        }
        favorites.save(Favorite.make(userId, templateId, clock.instant()));
    }

    @Transactional
    @Override
    public void remove(UserId userId, UUID templateId) {
        favorites.remove(userId, templateId);
    }
}
