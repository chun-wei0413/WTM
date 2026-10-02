package com.wtm.application.library;

import com.wtm.application.port.out.LibraryBrowsePort.LibraryCard;
import com.wtm.application.port.out.ObjectStoragePort;
import java.time.Duration;

/** Turns stored cards into what the pages show, by adding a temporary address for each picture. */
final class LibraryItems {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private LibraryItems() {
    }

    static LibraryItem of(LibraryCard card, ObjectStoragePort storage) {
        return new LibraryItem(card.id(), card.name(), storage.presignedGetUrl(card.imageKey(), IMAGE_URL_TTL),
                card.meaning(), card.tags(), card.imageText(), card.sourceType(), card.sourceUrl(),
                card.attribution());
    }
}
