package app.controller;

import app.model.Candidature;
import app.repository.CandidatureRepository;
import app.service.FileSystemService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import lombok.Getter;

import java.util.Comparator;

public class MainController {

    @Getter
    private final ObservableList<Candidature> candidatures;
    private final CandidatureRepository repository = new CandidatureRepository();

    public MainController(TableView<Candidature> table) {
        FileSystemService.init();
        candidatures = FXCollections.observableArrayList(repository.load());
        sort();
        table.setItems(candidatures);
    }

    public void add(Candidature c) {
        candidatures.add(c);
        sort();
        save();
    }

    public void delete(Candidature c) {
        candidatures.remove(c);
        sort();
        save();
    }

    public void save() {
        repository.save(candidatures);
    }

    private void sort() {
        candidatures.sort(
                Comparator.comparing(Candidature::getDateEnvoi).reversed()
        );
    }
}
