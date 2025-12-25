package app;

import app.controller.MainController;
import app.model.Candidature;
import app.model.DocumentFile;
import app.model.DocumentType;
import app.model.StatutCandidature;
import app.service.FileSystemService;
import app.service.PdfImportService;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;

public class MainApp extends Application {

    private TableView<Candidature> table;
    private MainController controller;
    private PdfViewerPane pdfViewerPane;

    @Override
    public void start(Stage stage) {

        FileSystemService.init();

        /* =========================
           TABLEVIEW CANDIDATURES
           ========================= */
        table = new TableView<>();

        table.setRowFactory(tv -> {
            TableRow<Candidature> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem editItem = new MenuItem("Modifier");
            editItem.setOnAction(e -> {
                Candidature c = row.getItem();
                if (c != null) editCandidature(c);
            });

            MenuItem deleteItem = new MenuItem("Supprimer");
            deleteItem.setOnAction(e -> {
                Candidature c = row.getItem();
                if (c != null) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Confirmation");
                    confirm.setHeaderText("Supprimer la candidature ?");
                    confirm.setContentText(c.getEntreprise() + " - " + c.getPoste());

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            controller.delete(c);
                            table.getItems().remove(c);
                        }
                    });
                }
            });

            contextMenu.getItems().addAll(editItem, deleteItem);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            return row;
        });

        TableColumn<Candidature, LocalDate> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getDateEnvoi()));

        TableColumn<Candidature, String> colEntreprise = new TableColumn<>("Entreprise");
        colEntreprise.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getEntreprise()));

        TableColumn<Candidature, String> colPoste = new TableColumn<>("Poste");
        colPoste.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getPoste()));

        TableColumn<Candidature, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getStatut().name()));

        table.getColumns().addAll(colDate, colEntreprise, colPoste, colStatut);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


        /* =========================
           CONTROLLER
           ========================= */
        controller = new MainController(table);

        /* =========================
           PDF VIEWER
           ========================= */
        pdfViewerPane = new PdfViewerPane(FXCollections.observableArrayList(), controller);

        VBox rightPane = new VBox(10, pdfViewerPane);
        rightPane.setPadding(new Insets(10));
        VBox.setVgrow(pdfViewerPane, Priority.ALWAYS);

        /* =========================
           SPLITPANE
           ========================= */
        SplitPane splitPane = new SplitPane(table, rightPane);
        splitPane.setDividerPositions(0.4);

        BorderPane root = new BorderPane(splitPane);



        /* =========================
           TRI ET PREMIERE SELECTION
           ========================= */
        table.getItems().sort((c1, c2) -> c2.getDateEnvoi().compareTo(c1.getDateEnvoi()));
        if (!table.getItems().isEmpty()) {
            table.getSelectionModel().select(0);
            table.requestFocus();

            Candidature first = table.getSelectionModel().getSelectedItem();
            if (first != null) {
                pdfViewerPane.setPdfList(first.getDocuments());
            }
        }

        /* =========================
           SELECTION CANDIDATURE
           ========================= */
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> {
            if (c == null) {
                pdfViewerPane.setPdfList(FXCollections.observableArrayList());
                return;
            }
            pdfViewerPane.setPdfList(c.getDocuments());
        });

        /* =========================
           MENU CONTEXTUEL PDF
           ========================= */
        ContextMenu docMenu = new ContextMenu();

        MenuItem changeType = new MenuItem("Modifier type");
        changeType.setOnAction(e -> {
            DocumentFile doc = pdfViewerPane.getPdfSelector().getSelectionModel().getSelectedItem();
            if (doc == null) return;

            ChoiceDialog<DocumentType> dialog =
                    new ChoiceDialog<>(doc.getType(), DocumentType.values());
            dialog.setTitle("Type du document");
            dialog.setHeaderText("Choisir le type");

            dialog.showAndWait().ifPresent(doc::setType);

            // Forcer refresh
            var selected = pdfViewerPane.getPdfSelector().getSelectionModel().getSelectedItem();
            pdfViewerPane.getPdfSelector().getItems().setAll(pdfViewerPane.getPdfSelector().getItems());
            pdfViewerPane.getPdfSelector().getSelectionModel().select(selected);

            controller.save();
        });

        MenuItem deleteDoc = new MenuItem("Supprimer");
        deleteDoc.setOnAction(e -> {
            DocumentFile doc = pdfViewerPane.getPdfSelector().getSelectionModel().getSelectedItem();
            if (doc == null) return;

            doc.getFichier().toFile().delete();
            Candidature c = table.getSelectionModel().getSelectedItem();
            if (c != null) c.getDocuments().remove(doc);

            pdfViewerPane.getPdfSelector().getItems().remove(doc);
            controller.save();
        });

        docMenu.getItems().addAll(changeType, deleteDoc);
        pdfViewerPane.getPdfSelector().setContextMenu(docMenu);

        /* =========================
           BOUTONS
           ========================= */
        Button addCandidature = new Button("Nouvelle candidature");
        addCandidature.setOnAction(e -> createCandidature(stage));

        Button importPdf = new Button("Importer PDF");
        importPdf.setOnAction(e -> {
            try {
                importPdf(stage);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        ToolBar toolbar = new ToolBar(addCandidature, importPdf);
        root.setTop(toolbar);

        /* =========================
           SCENE ET NAVIGATION CLAVIER
           ========================= */
        Scene scene = new Scene(root, 1300, 650);
        stage.setTitle("Gestion des candidatures");
        stage.setScene(scene);
        stage.show();

        table.setFocusTraversable(true);
        pdfViewerPane.setFocusTraversable(true);
        pdfViewerPane.getPdfSelector().setFocusTraversable(true);

        // Flèches gauche/droite pour changer de focus
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case LEFT -> {
                    if (pdfViewerPane.isFocused() || pdfViewerPane.getPdfSelector().isFocused()) {
                        table.requestFocus();
                        event.consume();
                    }
                }
                case RIGHT -> {
                    if (table.isFocused()) {
                        pdfViewerPane.getPdfSelector().requestFocus();
                        event.consume();
                    }
                }
            }
        });
    }

    /* =========================
       CREATION & MODIFICATION CANDIDATURE
       ========================= */
    private void editCandidature(Candidature c) {
        Dialog<Candidature> dialog = new Dialog<>();
        dialog.setTitle("Modifier candidature");

        ButtonType saveBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker(c.getDateEnvoi());
        TextField entrepriseField = new TextField(c.getEntreprise());
        TextField posteField = new TextField(c.getPoste());

        // ChoiceBox pour le statut
        ChoiceBox<StatutCandidature> statutChoice = new ChoiceBox<>(
                FXCollections.observableArrayList(StatutCandidature.values())
        );
        statutChoice.setValue(c.getStatut());

        VBox content = new VBox(10,
                new Label("Date de candidature"), datePicker,
                new Label("Entreprise"), entrepriseField,
                new Label("Poste"), posteField,
                new Label("Statut"), statutChoice
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                c.setDateEnvoi(datePicker.getValue());
                c.setEntreprise(entrepriseField.getText());
                c.setPoste(posteField.getText());
                c.setStatut(statutChoice.getValue()); // <- mise à jour du statut
                return c;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            controller.save();
            table.refresh();
        });
    }


    private void createCandidature(Stage stage) {
        Dialog<Candidature> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle candidature");

        ButtonType create = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        DatePicker date = new DatePicker(LocalDate.now());
        TextField entreprise = new TextField();
        TextField poste = new TextField();

        VBox content = new VBox(10,
                new Label("Date"), date,
                new Label("Entreprise"), entreprise,
                new Label("Poste"), poste
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn != create) return null;

            Candidature c = new Candidature(entreprise.getText(), poste.getText());
            c.setDateEnvoi(date.getValue());
            c.setStatut(StatutCandidature.EN_ATTENTE); // <- statut par défaut

            Path dossier = FileSystemService.createCandidatureFolder(
                    (c.getEntreprise() + "_" + c.getPoste()).replaceAll("\\W+", "_")
            );
            c.setDossier(dossier);

            return c;
        });

        dialog.showAndWait().ifPresent(controller::add);
    }

    private void importPdf(Stage stage) throws IOException {
        Candidature c = table.getSelectionModel().getSelectedItem();
        if (c == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer un PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));

        File f = chooser.showOpenDialog(stage);
        if (f == null) return;

// Charger le PDF pour récupérer sa date
        LocalDate pdfDate = null;
        try (PDDocument doc = Loader.loadPDF(f)) {
            // Récupère la date de création depuis les metadata du PDF
            var creationDate = doc.getDocumentInformation().getCreationDate();
            if (creationDate != null) {
                pdfDate = creationDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } else {
                // fallback : date du fichier si metadata absente
                pdfDate = Files.readAttributes(f.toPath(), BasicFileAttributes.class)
                        .creationTime()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

// Crée le DocumentFile
        DocumentFile doc = PdfImportService.importer(
                f.toPath(),
                c.getDossier(),
                DocumentType.REPONSE
        );

        c.getDocuments().add(doc);
        pdfViewerPane.setPdfList(c.getDocuments());
        controller.save();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
