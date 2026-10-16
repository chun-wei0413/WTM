package com.wtm.curation.usecase.port.in.query;

import com.wtm.curation.usecase.port.out.library.LibraryPort.LibraryStats;

public interface GetLibraryStatsUseCase {

    LibraryStats handle();
}
