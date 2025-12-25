package app.controller;

import app.model.Candidature;
import app.repository.CandidatureRepository;
import app.service.FileSystemService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import lombok.Getter;

public class MainController {

    @Getter
    private final ObservableList<Candidature> candidatures;
    private final CandidatureRepository repository = new CandidatureRepository();

    public MainController(TableView<Candidature> table) {
        FileSystemService.init();
        candidatures = FXCollections.observableArrayList(repository.load());
        table.setItems(candidatures);
    }

    public void add(Candidature c) {
        candidatures.add(c);
        repository.save(candidatures);
    }

    public void delete(Candidature c) {
        // Supprimer les fichiers associés si nécessaire
        // FileSystemService.deleteFolder(c.getDossier());

        // Supprimer de la liste interne
        candidatures.remove(c);
        save();
    }

    public void save() {
        repository.save(candidatures);
    }

}
