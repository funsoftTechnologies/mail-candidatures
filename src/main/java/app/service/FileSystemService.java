package app.service;

import app.model.DocumentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
        try {
            Path dossier = ROOT.resolve(nom);
            Files.createDirectories(dossier.resolve(DocumentType.ENVOI.getFolder()));
            Files.createDirectories(dossier.resolve(DocumentType.REPONSE.getFolder()));
            return dossier;
        } catch (IOException e) {
            throw new RuntimeException("Erreur création dossier candidature", e);
        }
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

    public static Path moveDocument(
            Path currentFile,
            Path dossierCandidature,
            DocumentType newType
    ) throws IOException {

        Path targetDir = dossierCandidature.resolve(newType.getFolder());
        Files.createDirectories(targetDir);

        Path target = targetDir.resolve(currentFile.getFileName());

        if (Files.exists(target)) {
            String name = target.getFileName().toString();
            String base = name.replace(".pdf", "");
            target = targetDir.resolve(base + "_" + System.currentTimeMillis() + ".pdf");
        }

        return Files.move(
                currentFile,
                target,
                StandardCopyOption.REPLACE_EXISTING
        );
    }


}
