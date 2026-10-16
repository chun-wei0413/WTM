package com.wtm.discovery.usecase.port.in.command;

import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import java.util.List;
import java.util.UUID;

public interface FavoritesUseCase {

    List<LibraryItem> list(UUID userId);

    /** @throws TemplateNotFoundException when there is no published meme with that id */
    void add(UUID userId, UUID templateId);

    void remove(UUID userId, UUID templateId);
}
