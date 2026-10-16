package com.wtm.discovery.usecase.port.in.command;

import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.shared.UserId;
import java.util.List;
import java.util.UUID;

public interface FavoritesUseCase {

    List<LibraryItem> list(UserId userId);

    /** @throws TemplateNotFoundException when there is no published meme with that id */
    void add(UserId userId, UUID templateId);

    void remove(UserId userId, UUID templateId);
}
