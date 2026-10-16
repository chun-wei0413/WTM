package com.wtm.discovery.usecase.port.in.command;

public interface SyncSearchIndexUseCase {

    record Result(int indexed, int removed, int failed) {
    }

    Result handle(int batchSize);
}
