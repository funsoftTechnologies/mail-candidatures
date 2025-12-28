package app;

import app.controller.MainController;
import app.model.Candidature;
import app.model.DocumentFile;
import app.model.DocumentType;
import app.model.StatutCandidature;
import app.service.FileSystemService;
import app.service.PdfImportService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Objects;

public class MainApp extends Application {

    private TableView<Candidature> table;
    private MainController controller;
    private PdfViewerPane pdfViewerPane;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private final Comparator<Candidature> candidatureComparator =
            Comparator.comparing(
                    Candidature::getDateEnvoi,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );

    @Override
    public void start(Stage stage) {

        FileSystemService.init();


        /* ========================= TABLEVIEW ========================= */
        table = new TableView<>();
        table.setFocusTraversable(true);
        table.setMinHeight(200);

        table.setRowFactory(tv -> {
            TableRow<Candidature> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();

            MenuItem edit = new MenuItem("Modifier");
            edit.setOnAction(e -> {
                Candidature c = row.getItem();
                if (c != null) editCandidature(c);
            });

            MenuItem delete = new MenuItem("Supprimer");
            delete.setOnAction(e -> {
                Candidature c = row.getItem();
                if (c == null) return;

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirmation");
                confirm.setHeaderText("Supprimer la candidature ?");
                confirm.setContentText(c.getEntreprise() + " - " + c.getPoste());
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.OK) {
                        try {
                            FileSystemService.deleteRecursively(c.getDossier());
                            controller.delete(c);
                            table.getItems().remove(c);
                            pdfViewerPane.setPdfList(FXCollections.observableArrayList(), null);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                            new Alert(Alert.AlertType.ERROR,
                                    "Impossible de supprimer les fichiers.").showAndWait();
                        }
                    }
                });
            });

            menu.getItems().addAll(edit, delete);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });

        TableColumn<Candidature, Number> colIndex = new TableColumn<>("N°");
        colIndex.setCellValueFactory(c ->
                new javafx.beans.property.ReadOnlyObjectWrapper<>(table.getItems().indexOf(c.getValue()) + 1)
        );
        colIndex.setSortable(false);
        colIndex.setPrefWidth(30);

        TableColumn<Candidature, LocalDate> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getDateEnvoi()));
        colDate.setPrefWidth(60);

        TableColumn<Candidature, String> colEntreprise = new TableColumn<>("Entreprise");
        colEntreprise.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getEntreprise()));

        TableColumn<Candidature, String> colPoste = new TableColumn<>("Poste");
        colPoste.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getPoste()));

        TableColumn<Candidature, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().getStatut().name()));
        colStatut.setPrefWidth(60);

        table.getColumns().addAll(colIndex, colDate, colEntreprise, colPoste, colStatut);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


        controller = new MainController(table);

        // Trier les candidatures par date (plus récent en premier)
        table.getItems().sort(candidatureComparator);



        /* ========================= PDF VIEWER ========================= */
        pdfViewerPane = new PdfViewerPane(FXCollections.observableArrayList(), controller);

        MenuItem changeDate = new MenuItem("Modifier date");
        MenuItem changeType = new MenuItem("Modifier type");
        MenuItem deleteDoc = new MenuItem("Supprimer");

        ContextMenu docMenu = new ContextMenu(changeDate, changeType, deleteDoc);

        // Menu contextuel PDF
        changeType.setOnAction(e -> {
            DocumentFile doc = pdfViewerPane.getPdfListView().getSelectionModel().getSelectedItem();
            if (doc == null) return;
            ChoiceDialog<DocumentType> dialog = new ChoiceDialog<>(doc.getType(), DocumentType.values());
            dialog.setTitle("Type du document");
            dialog.setHeaderText("Choisir le type");
            dialog.showAndWait().ifPresent(newType -> {
                try {
                    Path newPath = FileSystemService.moveDocument(doc.getFichier(),
                            table.getSelectionModel().getSelectedItem().getDossier(),
                            newType);
                    doc.setType(newType);
                    doc.setFichier(newPath);
                    controller.save();
                    pdfViewerPane.getPdfListView().refresh();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Impossible de déplacer le fichier").showAndWait();
                }
            });
        });

        deleteDoc.setOnAction(e -> {
            DocumentFile doc = pdfViewerPane.getPdfListView().getSelectionModel().getSelectedItem();
            if (doc == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer ce document ?");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    try { Files.deleteIfExists(doc.getFichier()); } catch (IOException ignored) {}
                    Candidature cand = table.getSelectionModel().getSelectedItem();
                    if (cand != null) cand.getDocuments().remove(doc);
                    pdfViewerPane.getPdfListView().getItems().remove(doc);
                    controller.save();
                    table.refresh();
                }
            });
        });

        changeDate.setOnAction(e -> {
            DocumentFile doc = pdfViewerPane.getPdfListView().getSelectionModel().getSelectedItem();
            if (doc == null) return;

            Dialog<LocalDateTime> dialog = new Dialog<>();
            dialog.setTitle("Modifier la date du document");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            DatePicker datePicker = new DatePicker(doc.getDateMail() != null ? doc.getDateMail().toLocalDate() : LocalDate.now());
            Spinner<Integer> hourSpinner = new Spinner<>(0, 23, doc.getDateMail() != null ? doc.getDateMail().getHour() : 12);
            Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, doc.getDateMail() != null ? doc.getDateMail().getMinute() : 0);
            HBox timeBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);
            VBox content = new VBox(10, new Label("Date"), datePicker, new Label("Heure"), timeBox);
            content.setPadding(new Insets(10));
            dialog.getDialogPane().setContent(content);

            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return LocalDateTime.of(datePicker.getValue(), LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue()));
                }
                return null;
            });

            dialog.showAndWait().ifPresent(newDate -> {
                doc.setDateMail(newDate);
                controller.save();
                Candidature cand = table.getSelectionModel().getSelectedItem();
                if (cand != null) {

                    var sortedDocs = FXCollections.observableArrayList(cand.getDocuments());
                    sortedDocs.sort((d1, d2) -> {
                        LocalDateTime dt1 = d1.getDateMail() != null ? d1.getDateMail() : LocalDateTime.MIN;
                        LocalDateTime dt2 = d2.getDateMail() != null ? d2.getDateMail() : LocalDateTime.MIN;
                        return dt2.compareTo(dt1);
                    });

                    pdfViewerPane.setPdfList(sortedDocs, cand);

                }
            });
        });


        pdfViewerPane.getPdfListView().setContextMenu(docMenu);

        pdfViewerPane.getPdfListView().setCellFactory(lv -> new ListCell<DocumentFile>() {
            @Override
            protected void updateItem(DocumentFile doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) {
                    setText(null);
                } else {
                    String dateStr = doc.getDateMail() != null ?
                            doc.getDateMail().format(dateFormatter) : "Date inconnue";
//                    setText(doc.getType() + " le" + dateStr + " - " + doc.getFichier().getFileName());
                    if(Objects.equals(doc.getType().toString(), "ENVOI"))
                        setText(doc.getType() + "       - " + dateStr + " - " + doc.getFichier().getFileName());
                    else
                        setText(doc.getType() + "  - " + dateStr + " - " + doc.getFichier().getFileName());
                }
            }
        });


        // TableView à gauche
        table.setMinHeight(0);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setMaxHeight(Double.MAX_VALUE);

        // PdfViewerPane à droite
        pdfViewerPane.setMinHeight(0);
        pdfViewerPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        pdfViewerPane.setMaxHeight(Double.MAX_VALUE);

        // SplitPane dans BorderPane
        SplitPane splitPane = new SplitPane(table, pdfViewerPane);
        splitPane.setDividerPositions(0.4);

        BorderPane root = new BorderPane();
        root.setCenter(splitPane);



        /* ========================= TOOLBAR ========================= */
        Button addCandidature = new Button("Nouvelle candidature");
        addCandidature.setOnAction(e -> createCandidature(stage));

        Button importPdf = new Button("Importer PDF");
        importPdf.setOnAction(e -> {
            try { importPdf(stage); } catch (IOException ex) { ex.printStackTrace(); }
        });

        root.setTop(new ToolBar(addCandidature, importPdf));

        /* ========================= SELECTION ========================= */
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> {
            if (c == null) {
                pdfViewerPane.getPdfListView().getItems().clear();
                return;
            }

            // On garde la même ObservableList pour que le scroll ne soit pas bloqué
            var pdfList = pdfViewerPane.getPdfListView().getItems();
            pdfList.setAll(c.getDocuments());

            // Trie par date décroissante
            pdfList.sort((d1, d2) -> {
                LocalDateTime dt1 = d1.getDateMail() != null ? d1.getDateMail() : LocalDateTime.MIN;
                LocalDateTime dt2 = d2.getDateMail() != null ? d2.getDateMail() : LocalDateTime.MIN;
                return dt2.compareTo(dt1);
            });

            // Sélection initiale uniquement si rien n'est sélectionné
            if (pdfViewerPane.getPdfListView().getSelectionModel().isEmpty() && !pdfList.isEmpty()) {
                pdfViewerPane.getPdfListView().getSelectionModel().select(0);
            }
        });



        // Sélectionner la première candidature
        Platform.runLater(() -> {
            if (!table.getItems().isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });

        Scene scene = new Scene(root, 1300, 650);
        stage.setScene(scene);
        stage.setTitle("Gestion des candidatures");

//        for (int i = 0; i < 20; i++) {
//            Candidature c = new Candidature("Entreprise " + i, "Poste " + i);
//            c.setDateEnvoi(LocalDate.now().minusDays(i));
//            table.getItems().add(c);
//        }
        stage.show();
    }

    /* ========================= EDIT CANDIDATURE ========================= */
    private void editCandidature(Candidature c) {
        Dialog<Candidature> dialog = new Dialog<>();
        dialog.setTitle("Modifier candidature");

        ButtonType saveBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker(c.getDateEnvoi());
        TextField entrepriseField = new TextField(c.getEntreprise());
        TextField posteField = new TextField(c.getPoste());
        ChoiceBox<StatutCandidature> statutChoice = new ChoiceBox<>(FXCollections.observableArrayList(StatutCandidature.values()));
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
                c.setStatut(statutChoice.getValue());
                return c;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            controller.save();
            table.getItems().sort(candidatureComparator);
        });
    }


    /* ========================= CREATE CANDIDATURE ========================= */
    private void createCandidature(Stage stage) {
        Dialog<Candidature> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle candidature");

        ButtonType create = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        DatePicker date = new DatePicker(LocalDate.now());
        TextField entreprise = new TextField();
        TextField poste = new TextField();

        ChoiceBox<StatutCandidature> statut =
                new ChoiceBox<>(FXCollections.observableArrayList(StatutCandidature.values()));
        statut.setValue(StatutCandidature.EN_ATTENTE);

        Button importPdfBtn = new Button("Importer PDF");
        final DocumentFile[] imported = new DocumentFile[1];

        importPdfBtn.setOnAction(e -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("PDF", "*.pdf")
                );
                File f = chooser.showOpenDialog(stage);
                if (f == null) return;

                imported[0] = new DocumentFile();
                imported[0].setFichier(f.toPath());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox content = new VBox(10,
                new Label("Date"), date,
                new Label("Entreprise"), entreprise,
                new Label("Poste"), poste,
                new Label("Statut"), statut,
                importPdfBtn
        );

        DatePicker mailDatePicker = new DatePicker(LocalDate.now());
        Spinner<LocalTime> mailTimeSpinner = createTimeSpinner();
        LocalDateTime mailDateTime = LocalDateTime.of( mailDatePicker.getValue(), mailTimeSpinner.getValue() );

        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn != create) return null;

            Candidature c = new Candidature(entreprise.getText(), poste.getText());
            c.setDateEnvoi(date.getValue());
            c.setStatut(statut.getValue());

            Path dossier = FileSystemService.createCandidatureFolder(
                    (c.getEntreprise() + "_" + c.getPoste()).replaceAll("\\W+", "_")
            );
            c.setDossier(dossier);

            if (imported[0] != null) {
                try {
                    DocumentFile doc = PdfImportService.importer(
                            imported[0].getFichier(),
                            dossier,
                            DocumentType.REPONSE,
                            mailDateTime
                    );
                    c.getDocuments().add(doc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return c;
        });
        dialog.showAndWait().ifPresent(c -> {
            controller.add(c);
            table.getItems().sort(candidatureComparator);
        });
    }


    /* ========================= IMPORT PDF ========================= */
    private void importPdf(Stage stage) throws IOException {
        Candidature c = table.getSelectionModel().getSelectedItem();
        if (c == null) return;

        ChoiceDialog<DocumentType> typeDialog = new ChoiceDialog<>(DocumentType.REPONSE, DocumentType.values());
        typeDialog.setTitle("Type du document"); typeDialog.setHeaderText("Choisir le type du PDF");
        var typeOpt = typeDialog.showAndWait(); if (typeOpt.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = chooser.showOpenDialog(stage); if (f == null) return;

        // Saisie date mail
        Dialog<LocalDateTime> dateDialog = new Dialog<>();
        dateDialog.setTitle("Date du mail");
        dateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DatePicker datePicker = new DatePicker(LocalDate.now());
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, LocalTime.now().getHour());
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, LocalTime.now().getMinute());
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Date:"), 0,0); grid.add(datePicker, 1,0);
        grid.add(new Label("Heure:"), 0,1); grid.add(hourSpinner, 1,1);
        grid.add(new Label("Minute:"), 2,1); grid.add(minuteSpinner, 3,1);
        dateDialog.getDialogPane().setContent(grid);

        dateDialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) return LocalDateTime.of(datePicker.getValue(),
                    LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue()));
            return null;
        });

        var mailDateOpt = dateDialog.showAndWait(); if (mailDateOpt.isEmpty()) return;

        DocumentFile doc = PdfImportService.importer(f.toPath(), c.getDossier(), typeOpt.get(), mailDateOpt.get());
        c.getDocuments().add(doc);
        controller.save();

        var sortedDocs = FXCollections.observableArrayList(c.getDocuments());
        sortedDocs.sort((d1, d2) -> {
            LocalDateTime dt1 = d1.getDateMail() != null ? d1.getDateMail() : LocalDateTime.MIN;
            LocalDateTime dt2 = d2.getDateMail() != null ? d2.getDateMail() : LocalDateTime.MIN;
            return dt2.compareTo(dt1);
        });

        pdfViewerPane.setPdfList(sortedDocs, c);
        Platform.runLater(() -> {
            if (!sortedDocs.isEmpty()) {
                pdfViewerPane.getPdfListView()
                        .getSelectionModel()
                        .select(0);
            }
        });

    }

    private Spinner<LocalTime> createTimeSpinner() {
        Spinner<LocalTime> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory<>() {
            { setValue(LocalTime.now()); }
            @Override public void decrement(int steps) { setValue(getValue().minusMinutes(steps)); }
            @Override public void increment(int steps) { setValue(getValue().plusMinutes(steps)); }
        });
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
        return spinner;
    }

    public static void main(String[] args) { launch(args); }
}
