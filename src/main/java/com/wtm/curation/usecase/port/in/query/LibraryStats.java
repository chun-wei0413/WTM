package com.wtm.curation.usecase.port.in.query;

public record LibraryStats(int total, int approved, int retired, int waitingForTags, int beingTagged,
                    int tagFailures) {
}
