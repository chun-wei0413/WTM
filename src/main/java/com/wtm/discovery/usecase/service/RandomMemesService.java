package com.wtm.discovery.usecase.service;

import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.discovery.usecase.port.in.query.RandomMemesUseCase;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort;
import com.wtm.shared.port.ObjectStoragePort;
import java.util.List;

/**
 * Some memes picked at random from the library, for browsing when there is nothing to search for yet.
 */
public class RandomMemesService implements RandomMemesUseCase {

    private static final int MAX = 50;

    private final LibraryBrowsePort library;
    private final ObjectStoragePort storage;

    public RandomMemesService(LibraryBrowsePort library, ObjectStoragePort storage) {
        this.library = library;
        this.storage = storage;
    }

    @Override
    public List<LibraryItem> handle(int limit) {
        int count = Math.min(Math.max(limit, 1), MAX);
        return library.random(count).stream().map(card -> LibraryItems.of(card, storage)).toList();
    }
}
