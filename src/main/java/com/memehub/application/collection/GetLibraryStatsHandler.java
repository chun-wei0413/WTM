package com.memehub.application.collection;

import com.memehub.application.port.out.LibraryPort;
import com.memehub.application.port.out.LibraryPort.LibraryStats;

public class GetLibraryStatsHandler {

    private final LibraryPort library;

    public GetLibraryStatsHandler(LibraryPort library) {
        this.library = library;
    }

    public LibraryStats handle() {
        return library.stats();
    }
}
