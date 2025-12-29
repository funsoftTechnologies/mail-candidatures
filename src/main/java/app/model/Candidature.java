package app.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Candidature {

    private String id;
    private String entreprise;
    private String poste;
    private LocalDate dateEnvoi;
    private StatutCandidature statut;
    private Path dossier;
    private List<DocumentFile> documents = new ArrayList<>();

    // === Nouveaux champs ===
    private String notes;
    private LocalDate dateRelance;

    public Candidature(String entreprise, String poste) {
        this.id = UUID.randomUUID().toString();
        this.entreprise = entreprise;
        this.poste = poste;
        this.statut = StatutCandidature.EN_ATTENTE;
    }

    public void ajouterDocument(DocumentFile doc) {
        documents.add(doc);
    }
}
