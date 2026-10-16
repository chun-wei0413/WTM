package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.ImportUploadsUseCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Adds many pictures at once, for example a whole folder picked in the browser (administrators only). */
@RestController
class ImportFilesController {

    private final ImportUploadsUseCase uploads;

    ImportFilesController(ImportUploadsUseCase uploads) {
        this.uploads = uploads;
    }

    @PostMapping(value = "/api/admin/collection/files", consumes = "multipart/form-data")
    List<ImportedFile> addFiles(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<ImportUploadsUseCase.Upload> batch = new ArrayList<>();
        for (MultipartFile file : files) {
            batch.add(new ImportUploadsUseCase.Upload(file.getOriginalFilename(), file.getBytes()));
        }
        return uploads.handle(batch).stream().map(ImportedFile::of).toList();
    }
}
