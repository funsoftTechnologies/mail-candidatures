package app.service;

import app.model.DocumentFile;
import app.model.DocumentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

public class PdfImportService {

    public static DocumentFile importer(
            Path sourcePdf,
            Path dossierCandidature,
            DocumentType type
    ) throws IOException {

        Files.createDirectories(dossierCandidature);

        Path dest = dossierCandidature.resolve(sourcePdf.getFileName());

        Files.copy(sourcePdf, dest, StandardCopyOption.REPLACE_EXISTING);

        return new DocumentFile(
                dest,
                type,
                LocalDateTime.now(),
                sourcePdf.getFileName().toString()
        );
    }
}
