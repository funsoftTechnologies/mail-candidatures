package app.service;

import app.model.Candidature;
import app.model.DocumentFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class FileSystemService {

    private static final Path ROOT =
            Paths.get(System.getProperty("user.home"), "Candidatures");

    public static Path getRoot() {
        return ROOT;
    }

    public static void init() {
        try {
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le dossier racine", e);
        }
    }

    public static Path createCandidatureFolder(String nom) {
        return ROOT.resolve(nom);
    }


    public static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;

        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // enfants avant parents
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Impossible de supprimer : " + p, e);
                    }
                });
    }

    public static Path renameCandidatureFolderWithOldestPdfDate(Candidature c) throws IOException {

        if (c.getDocuments() == null || c.getDocuments().isEmpty()) {
            return c.getDossier();
        }

        var oldestOpt = c.getDocuments().stream()
                .map(DocumentFile::getDateMail)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo);

        if (oldestOpt.isEmpty()) {
            return c.getDossier();
        }

        String datePrefix = oldestOpt.get()
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        String entreprise = c.getEntreprise()
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .trim();

        String poste = c.getPoste()
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .trim();

        String newFolderName = datePrefix + " " + entreprise + " " + poste;

        Path oldPath = c.getDossier();
        Path newPath = oldPath.getParent().resolve(newFolderName);

        if (oldPath.equals(newPath)) {
            return oldPath;
        }

        Files.move(oldPath, newPath);

        // 🔴 POINT CRITIQUE : mettre à jour les chemins des documents
        for (DocumentFile doc : c.getDocuments()) {
            Path oldFile = doc.getFichier();
            if (oldFile != null) {
                Path newFile = newPath.resolve(oldFile.getFileName());
                doc.setFichier(newFile);
            }
        }

        c.setDossier(newPath);
        return newPath;
    }

}
