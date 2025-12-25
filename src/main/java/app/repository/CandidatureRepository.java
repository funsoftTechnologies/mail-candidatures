package app.repository;

import app.model.Candidature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CandidatureRepository {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // support Java 8 Date/Time
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // écrire en ISO-8601

    private static final File FILE = new File(System.getProperty("user.home"), "candidatures.json");
    private static List<Candidature> candidatures = new ArrayList<>();

    public static List<Candidature> getAll() {
        return candidatures;
    }

    public static void add(Candidature c) {
        candidatures.add(c);
        save(candidatures);
    }

    public static void save(List<Candidature> list) {
        try {
            mapper.writeValue(FILE, list);
        } catch (IOException e) {
            throw new RuntimeException("Erreur écriture JSON", e);
        }
    }


    public static List<Candidature> load() {
        if (!FILE.exists()) return new ArrayList<>();
        try {
            candidatures = mapper.readValue(FILE,
                    mapper.getTypeFactory().constructCollectionType(List.class, Candidature.class));
            return candidatures;  // <-- retourner la liste
        } catch (IOException e) {
            throw new RuntimeException("Erreur lecture JSON", e);
        }
    }
}
