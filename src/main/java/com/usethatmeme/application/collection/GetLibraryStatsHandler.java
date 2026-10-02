package com.usethatmeme.application.collection;

import com.usethatmeme.application.port.out.LibraryPort;
import com.usethatmeme.application.port.out.LibraryPort.LibraryStats;

public class GetLibraryStatsHandler {

    private final LibraryPort library;

    public GetLibraryStatsHandler(LibraryPort library) {
        this.library = library;
    }

    public LibraryStats handle() {
        return library.stats();
    }
}
