package app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            Files.createDirectories(dossier.resolve("envoi"));
            Files.createDirectories(dossier.resolve("reponses"));
            return dossier;
        } catch (IOException e) {
            throw new RuntimeException("Erreur création dossier candidature", e);
        }
    }
}
