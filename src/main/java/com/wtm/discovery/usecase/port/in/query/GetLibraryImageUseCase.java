package com.wtm.discovery.usecase.port.in.query;

import java.util.UUID;

public interface GetLibraryImageUseCase {

    record ImageFile(byte[] content, String contentType, String filename) {
    }

    ImageFile handle(UUID templateId);
}
