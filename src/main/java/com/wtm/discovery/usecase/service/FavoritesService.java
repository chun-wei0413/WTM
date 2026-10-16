package com.wtm.discovery.usecase.service;

import com.wtm.discovery.usecase.port.in.command.FavoritesUseCase;
import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.discovery.usecase.port.out.favorite.FavoritePort;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.port.ObjectStoragePort;
import java.util.List;
import java.util.UUID;

/**
 * A user's own shortlist of library memes.
 */
public class FavoritesService implements FavoritesUseCase {

    private final FavoritePort favorites;
    private final ObjectStoragePort storage;

    public FavoritesService(FavoritePort favorites, ObjectStoragePort storage) {
        this.favorites = favorites;
        this.storage = storage;
    }

    @Override
    public List<LibraryItem> list(UUID userId) {
        return favorites.list(userId).stream().map(card -> LibraryItems.of(card, storage)).toList();
    }

    /** @throws TemplateNotFoundException when there is no published meme with that id */
    @Override
    public void add(UUID userId, UUID templateId) {
        if (!favorites.add(userId, templateId)) {
            throw new TemplateNotFoundException(new TemplateId(templateId));
        }
    }

    @Override
    public void remove(UUID userId, UUID templateId) {
        favorites.remove(userId, templateId);
    }
}
