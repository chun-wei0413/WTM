package com.wtm.discovery.usecase.service;

import com.wtm.discovery.usecase.port.in.query.GetLibraryImageUseCase;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryImage;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.port.ObjectStoragePort;
import java.util.UUID;

/**
 * The picture of a published library meme, for downloading or for drawing on.
 */
public class GetLibraryImageService implements GetLibraryImageUseCase {

    private final LibraryBrowsePort library;
    private final ObjectStoragePort storage;

    public GetLibraryImageService(LibraryBrowsePort library, ObjectStoragePort storage) {
        this.library = library;
        this.storage = storage;
    }

    @Override
    public ImageFile handle(UUID templateId) {
        LibraryImage image = library.findPublishedImage(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(new TemplateId(templateId)));
        String key = image.imageKey().toLowerCase();
        String extension = key.endsWith(".png") ? "png" : key.endsWith(".gif") ? "gif" : "jpg";
        String contentType = switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
        return new ImageFile(storage.get(image.imageKey()), contentType, "meme-" + templateId + "." + extension);
    }

}
