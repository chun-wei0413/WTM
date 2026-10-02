package com.wtm.application.library;

import com.wtm.application.port.out.LibraryBrowsePort;
import com.wtm.application.port.out.ObjectStoragePort;
import java.util.List;

/**
 * Some memes picked at random from the library, for browsing when there is nothing to search for yet.
 */
public class RandomMemesHandler {

    private static final int MAX = 50;

    private final LibraryBrowsePort library;
    private final ObjectStoragePort storage;

    public RandomMemesHandler(LibraryBrowsePort library, ObjectStoragePort storage) {
        this.library = library;
        this.storage = storage;
    }

    public List<LibraryItem> handle(int limit) {
        int count = Math.min(Math.max(limit, 1), MAX);
        return library.random(count).stream().map(card -> LibraryItems.of(card, storage)).toList();
    }
}
