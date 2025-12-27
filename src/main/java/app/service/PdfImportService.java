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
            DocumentType type,
            LocalDateTime dateMail
    )
throws IOException {

        // 1. Sous-dossier selon le type
        Path targetDir = dossierCandidature.resolve(type.getFolder());
        Files.createDirectories(targetDir);

        String fileName = sourcePdf.getFileName().toString();
        Path target = targetDir.resolve(sourcePdf.getFileName());

        // 2. Éviter écrasement silencieux
        if (Files.exists(target)) {
            String base = fileName.replaceFirst("(?i)\\.pdf$", "");
            String newName = base + "_" + System.currentTimeMillis() + ".pdf";
            target = targetDir.resolve(newName);
        }

        // 3. MOVE réel
        Files.move(
                sourcePdf,
                target,
                StandardCopyOption.REPLACE_EXISTING
                // ⚠ ATOMIC_MOVE à éviter ici (voir plus bas)
        );

        // 4. DocumentFile cohérent
        DocumentFile doc = new DocumentFile();
        doc.setNom(target.getFileName().toString());
        doc.setFichier(target);
        doc.setType(type);
        doc.setDateMail(
                dateMail != null ? dateMail : LocalDateTime.now()
        );
        return doc;
    }

}
