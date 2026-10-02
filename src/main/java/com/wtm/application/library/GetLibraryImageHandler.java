package com.wtm.application.library;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.port.out.LibraryBrowsePort;
import com.wtm.application.port.out.LibraryBrowsePort.LibraryImage;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.domain.template.TemplateId;
import java.util.UUID;

/**
 * The picture of a published library meme, for downloading or for drawing on.
 */
public class GetLibraryImageHandler {

    private final LibraryBrowsePort library;
    private final ObjectStoragePort storage;

    public GetLibraryImageHandler(LibraryBrowsePort library, ObjectStoragePort storage) {
        this.library = library;
        this.storage = storage;
    }

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

    public record ImageFile(byte[] content, String contentType, String filename) {
    }
}
