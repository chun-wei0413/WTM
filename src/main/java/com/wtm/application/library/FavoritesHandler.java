package com.wtm.application.library;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.port.out.FavoritePort;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.domain.template.TemplateId;
import java.util.List;
import java.util.UUID;

/**
 * A user's own shortlist of library memes.
 */
public class FavoritesHandler {

    private final FavoritePort favorites;
    private final ObjectStoragePort storage;

    public FavoritesHandler(FavoritePort favorites, ObjectStoragePort storage) {
        this.favorites = favorites;
        this.storage = storage;
    }

    public List<LibraryItem> list(UUID userId) {
        return favorites.list(userId).stream().map(card -> LibraryItems.of(card, storage)).toList();
    }

    /** @throws TemplateNotFoundException when there is no published meme with that id */
    public void add(UUID userId, UUID templateId) {
        if (!favorites.add(userId, templateId)) {
            throw new TemplateNotFoundException(new TemplateId(templateId));
        }
    }

    public void remove(UUID userId, UUID templateId) {
        favorites.remove(userId, templateId);
    }
}
