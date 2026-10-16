package com.wtm.curation.usecase.service;

import com.wtm.curation.usecase.port.in.query.GetLibraryStatsUseCase;
import com.wtm.curation.usecase.port.in.query.LibraryStats;
import com.wtm.curation.usecase.port.out.library.LibraryPort;

public class GetLibraryStatsService implements GetLibraryStatsUseCase {

    private final LibraryPort library;

    public GetLibraryStatsService(LibraryPort library) {
        this.library = library;
    }

    @Override
    public LibraryStats handle() {
        return library.stats();
    }
}
