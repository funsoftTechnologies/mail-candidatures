package app;

import app.controller.MainController;
import app.model.Candidature;
import app.model.DocumentFile;
import app.model.StatutCandidature;
import app.service.CandidatureService;
import app.service.FileSystemService;
import app.service.PdfImportService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH );
    private final Pattern dateTimePattern = Pattern.compile(
            "(\\d{1,2}\\s(?:janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)\\s\\d{4}\\sà\\s\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE );

    private FilteredList<Candidature> filteredCandidatures;
    private SortedList<Candidature> sortedCandidatures;


    @Override
    public void start(Stage stage) {

        FileSystemService.init();


        /* ========================= TABLEVIEW ========================= */
        table = new TableView<>();
        table.setFocusTraversable(true);
        table.setMinHeight(200);

        table.setRowFactory(tv -> {
            TableRow<Candidature> row = new TableRow<>();

//            /* ===== Style conditionnel ===== */
//            row.itemProperty().addListener((obs, old, c) -> {
//                if (c == null) {
//                    row.setStyle("");
//                    return;
//                }
//
//                if (c.getStatut() == StatutCandidature.REFUS) {
//                    row.setStyle("-fx-background-color: #ffe5e5;");
//                } else {
//                    row.setStyle("");
//                }
//            });

            /* ===== Menu contextuel  ===== */
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
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });

        TableColumn<Candidature, Number> colIndex = new TableColumn<>("N°");
        colIndex.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(table.getItems().indexOf(c.getValue()) + 1)
        );
        colIndex.setSortable(false);
        colIndex.setPrefWidth(30);

        TableColumn<Candidature, LocalDate> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getDateEnvoi()));
        colDate.setPrefWidth(60);
        colDate.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(colDate);

        TableColumn<Candidature, String> colEntreprise = new TableColumn<>("Entreprise");
        colEntreprise.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEntreprise()));

        TableColumn<Candidature, String> colPoste = new TableColumn<>("Poste");
        colPoste.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPoste()));

        TableColumn<Candidature, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatut().getLabel()));
        colStatut.setPrefWidth(60);

        table.getColumns().addAll(colIndex, colDate, colEntreprise, colPoste, colStatut);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item);

                Candidature c = getTableView().getItems().get(getIndex());
                if (c.getStatut() == StatutCandidature.REFUS) {
                    setStyle("-fx-text-fill: #cc4444;");
                } else {
                    setStyle("");
                }
            }
        });

// Colonne Notes
        TableColumn<Candidature, String> colNotes = new TableColumn<>("Notes");
        colNotes.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotes()));
        colNotes.setCellFactory(TextFieldTableCell.forTableColumn());
        colNotes.setOnEditCommit(event -> {
            Candidature c = event.getRowValue();
            c.setNotes(event.getNewValue());
            controller.save();
        });

// Colonne Date de relance
        TableColumn<Candidature, LocalDate> colRelance = new TableColumn<>("Relance");
        colRelance.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDateRelance()));
        colRelance.setCellFactory(col -> new DatePickerTableCell<>());
        colRelance.setOnEditCommit(event -> {
            Candidature c = event.getRowValue();
            c.setDateRelance(event.getNewValue());
            controller.save();
        });

// Colonne Temps écoulé depuis envoi
        TableColumn<Candidature, String> colElapsed = new TableColumn<>("Depuis envoi");
        colElapsed.setCellValueFactory(c -> {
            LocalDate date = c.getValue().getDateEnvoi();
            if (date == null) return new SimpleStringProperty("");
            long days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now());
            return new SimpleStringProperty(days + " jours");
        });

// Ajouter les nouvelles colonnes
        table.getColumns().addAll(colNotes, colRelance, colElapsed);
        table.setEditable(true);




        controller = new MainController(table);

        filteredCandidatures =
                new FilteredList<>(controller.getCandidatures(), c -> true);

        sortedCandidatures =
                new SortedList<>(filteredCandidatures);

        sortedCandidatures.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedCandidatures);




        /* ========================= PDF VIEWER ========================= */
        pdfViewerPane = new PdfViewerPane(FXCollections.observableArrayList(), controller);

        MenuItem importPdfDoc = new MenuItem("Importer PDF");
        MenuItem changeDate = new MenuItem("Modifier date");
        MenuItem deleteDoc = new MenuItem("Supprimer");

        ContextMenu docMenu = new ContextMenu(importPdfDoc, changeDate, deleteDoc);

        // Menu contextuel PDF
        importPdfDoc.setOnAction(e -> {
            importPdf(stage);  // On réutilise ta méthode existante
        });

//        changeType.setOnAction(e -> {
//            DocumentFile doc = pdfViewerPane.getPdfListView().getSelectionModel().getSelectedItem();
//            if (doc == null) return;
//            ChoiceDialog<DocumentType> dialog = new ChoiceDialog<>(doc.getType(), DocumentType.values());
//            dialog.setTitle("Type du document");
//            dialog.setHeaderText("Choisir le type");
//            dialog.showAndWait().ifPresent(newType -> {
//                try {
//                    Path newPath = FileSystemService.moveDocument(doc.getFichier(),
//                            table.getSelectionModel().getSelectedItem().getDossier(),
//                            newType);
//                    doc.setType(newType);
//                    doc.setFichier(newPath);
//                    controller.save();
//                    pdfViewerPane.getPdfListView().refresh();
//                } catch (IOException ex) {
//                    ex.printStackTrace();
//                    new Alert(Alert.AlertType.ERROR, "Impossible de déplacer le fichier").showAndWait();
//                }
//            });
//        });

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
                    setText(dateStr + " - " + doc.getFichier().getFileName());
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
// 1️⃣ Déclaration UI
        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher entreprise ou poste...");

        searchField.setPrefWidth(250);

        searchField.textProperty().addListener((obs, old, text) -> {
            filteredCandidatures.setPredicate(c -> {
                if (text == null || text.isBlank()) return true;
                String lower = text.toLowerCase();
                return c.getEntreprise().toLowerCase().contains(lower)
                        || c.getPoste().toLowerCase().contains(lower);
            });
        });

        ChoiceBox<StatutCandidature> statutFilter =
                new ChoiceBox<>(FXCollections.observableArrayList(StatutCandidature.values()));
        statutFilter.setPrefWidth(140);
        statutFilter.setValue(StatutCandidature.TOUTES);


        statutFilter.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            filteredCandidatures.setPredicate(c -> {
                if (selected == null || selected == StatutCandidature.TOUTES) {
                    return true;
                }
                return c.getStatut() == selected;
            });
        });


        Button addCandidature = new Button("Nouvelle candidature");
        addCandidature.setOnAction(e -> createCandidature(stage));

        Button rapportStatBtn = new Button("Rapport Stat");
        rapportStatBtn.setOnAction(e -> showStatWindow());

        rapportStatBtn.setTooltip(new Tooltip("Voir les statistiques des candidatures"));





        ChoiceBox<String> moisFilter = new ChoiceBox<>();
        moisFilter.getItems().add("Tous");
        for (int m = 1; m <= 12; m++) moisFilter.getItems().add(String.valueOf(m));
        moisFilter.setValue("Tous");

        CheckBox pdfFilter = new CheckBox("Avec PDF");
        CheckBox responseFilter = new CheckBox("Avec réponse");

// 2️⃣ Ajouter à la barre d'outils
        root.setTop(new ToolBar(
                addCandidature,
                rapportStatBtn,
                new Separator(),
                new Label("Filtre :"),
                statutFilter,
                moisFilter,
                pdfFilter,
                responseFilter,
                searchField
        ));

// 3️⃣ Lier les listeners pour mettre à jour le predicate
        searchField.textProperty().addListener((obs, old, text) ->
                updatePredicate(filteredCandidatures, searchField, statutFilter, moisFilter, pdfFilter, responseFilter));

        statutFilter.valueProperty().addListener((obs, old, val) ->
                updatePredicate(filteredCandidatures, searchField, statutFilter, moisFilter, pdfFilter, responseFilter));

        moisFilter.valueProperty().addListener((obs, old, val) ->
                updatePredicate(filteredCandidatures, searchField, statutFilter, moisFilter, pdfFilter, responseFilter));

        pdfFilter.selectedProperty().addListener((obs, old, val) ->
                updatePredicate(filteredCandidatures, searchField, statutFilter, moisFilter, pdfFilter, responseFilter));

        responseFilter.selectedProperty().addListener((obs, old, val) ->
                updatePredicate(filteredCandidatures, searchField, statutFilter, moisFilter, pdfFilter, responseFilter));


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

        Platform.runLater(this::renameAllFolders);


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
        Platform.runLater(() -> centerStage(stage));

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
            table.refresh();
        });
    }

    private void showStatWindow() {
        CandidatureService service = new CandidatureService(table.getItems());

        Stage statStage = new Stage();
        statStage.setTitle("Rapport Statistiques Candidatures");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        VBox topBox = new VBox(10);
        Label totalLabel = new Label("Total candidatures : " + service.getTotal());
        long reponses = service.countByStatut(StatutCandidature.ENTRETIEN)
                + service.countByStatut(StatutCandidature.REFUS);
        Label reponseLabel = new Label("Réponses reçues : " + reponses);
        topBox.getChildren().addAll(totalLabel, reponseLabel);

        PieChart pieChart = new PieChart();
        pieChart.getData().addAll(
                new PieChart.Data("Acceptées", service.countByStatut(StatutCandidature.ENTRETIEN)),
                new PieChart.Data("Refusées", service.countByStatut(StatutCandidature.REFUS)),
                new PieChart.Data("En attente", service.countByStatut(StatutCandidature.EN_ATTENTE))
        );

        // Permet au PieChart de prendre tout l’espace disponible
        root.setTop(topBox);
        root.setCenter(pieChart);

        Scene scene = new Scene(root, 800, 600); // taille initiale plus grande
        statStage.setScene(scene);
        statStage.setMinWidth(600);
        statStage.setMinHeight(500);
        statStage.setResizable(true);

        statStage.show();
        statStage.centerOnScreen();
    }




    /* ========================= CREATE CANDIDATURE ========================= */
    private void createCandidature(Stage stage) {
        Dialog<Candidature> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle candidature");

        centerDialog(dialog, stage);

        ButtonType create = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        DatePicker date = new DatePicker(LocalDate.now());
        TextField entreprise = new TextField();
        TextField poste = new TextField();

//        ChoiceBox<StatutCandidature> statut =
//                new ChoiceBox<>(FXCollections.observableArrayList(StatutCandidature.values()));
//        statut.setValue(StatutCandidature.EN_ATTENTE);

//        Button importPdfBtn = new Button("Importer PDF");
//        final DocumentFile[] imported = new DocumentFile[1];

//        importPdfBtn.setOnAction(e -> {
//            try {
//                FileChooser chooser = new FileChooser();
//                chooser.getExtensionFilters().add(
//                        new FileChooser.ExtensionFilter("PDF", "*.pdf")
//                );
//                File f = chooser.showOpenDialog(stage);
//                if (f == null) return;
//
//                imported[0] = new DocumentFile();
//                imported[0].setFichier(f.toPath());
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });

        VBox content = new VBox(10,
                new Label("Date"), date,
                new Label("Entreprise"), entreprise,
                new Label("Poste"), poste
//                new Label("Statut"), statut
//                importPdfBtn
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
            c.setStatut(StatutCandidature.EN_ATTENTE);

            Path dossier = FileSystemService.createCandidatureFolder(
                    (c.getEntreprise() + "_" + c.getPoste()).replaceAll("\\W+", "_")
            );
            c.setDossier(dossier);

            return c;
        });
        dialog.showAndWait().ifPresent(c -> {
            controller.add(c);

            // Sélectionner la candidature créée
            Platform.runLater(() -> {
                table.getSelectionModel().select(c);
                table.scrollTo(c);
            });
        });
    }

    private void centerDialog(Dialog<?> dialog, Window owner) {
        dialog.initOwner(owner);

        dialog.setOnShown(e -> {
            Window w = dialog.getDialogPane().getScene().getWindow();
            w.setX(owner.getX() + (owner.getWidth() - w.getWidth()) / 2);
            w.setY(owner.getY() + (owner.getHeight() - w.getHeight()) / 2);
        });
    }

    private void centerStage(Stage stage) {
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }


    /* ========================= IMPORT PDF ========================= */

    private void importPdf(Stage stage) {
        Candidature c = table.getSelectionModel().getSelectedItem();
        if (c == null) return;

        // 1️⃣ Ouvrir FileChooser sur thread UI uniquement
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = chooser.showOpenDialog(stage);
        if (f == null) return;

        // ✅ FileChooser fermé, UI thread libre, on peut lancer un thread lourd
        stage.getScene().setCursor(Cursor.WAIT);

        new Thread(() -> {
            LocalDateTime dt = null;
            boolean found = false;

            // 2️⃣ Lecture PDF + extraction date (votre méthode originale)
            try (PDDocument document = PDDocument.load(f)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                Matcher matcher = dateTimePattern.matcher(text);
                while (matcher.find()) {
                    String dateStr = matcher.group(1);
                    try {
                        dt = LocalDateTime.parse(dateStr, formatter);
                        found = true;
                    } catch (DateTimeParseException ignored) {}
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            // 3️⃣ Déplacer ou copier le fichier après fermeture PDF
            DocumentFile doc = null;
            try {
                doc = PdfImportService.importer(f.toPath(), c.getDossier(), dt);
            } catch (IOException e) {
                e.printStackTrace();
            }

            DocumentFile finalDoc = doc;
            boolean finalFound = found;

            // 4️⃣ Mettre à jour UI via Platform.runLater
            Platform.runLater(() -> {
                stage.getScene().setCursor(Cursor.DEFAULT);

                if (finalDoc != null) {
                    c.getDocuments().add(finalDoc);
                    controller.save();

                    var sortedDocs = FXCollections.observableArrayList(c.getDocuments());
                    sortedDocs.sort((d1, d2) -> {
                        LocalDateTime dt1 = d1.getDateMail() != null ? d1.getDateMail() : LocalDateTime.MIN;
                        LocalDateTime dt2 = d2.getDateMail() != null ? d2.getDateMail() : LocalDateTime.MIN;
                        return dt2.compareTo(dt1);
                    });

                    pdfViewerPane.setPdfList(sortedDocs, c);
                    if (!sortedDocs.isEmpty()) {
                        pdfViewerPane.getPdfListView().getSelectionModel().select(0);
                    }
                }

                if (!finalFound) {
                    new Alert(Alert.AlertType.INFORMATION, "Aucune date détectée dans ce PDF.").showAndWait();
                }
            });

        }, "pdf-import-thread").start();
    }


    private void updatePredicate(FilteredList<Candidature> filtered,
                                 TextField searchField,
                                 ChoiceBox<StatutCandidature> statutFilter,
                                 ChoiceBox<String> moisFilter,
                                 CheckBox pdfFilter,
                                 CheckBox responseFilter) {

        String text = searchField.getText().toLowerCase();
        StatutCandidature statut = statutFilter.getValue();
        String mois = moisFilter.getValue();
        boolean filterPDF = pdfFilter.isSelected();
        boolean filterResponse = responseFilter.isSelected();

        filtered.setPredicate(c -> {
            boolean matchSearch = text.isEmpty() || c.getEntreprise().toLowerCase().contains(text)
                    || c.getPoste().toLowerCase().contains(text);
            boolean matchStatut = statut == StatutCandidature.TOUTES || c.getStatut() == statut;
            boolean matchMonth = mois.equals("Tous") || (c.getDateEnvoi() != null && c.getDateEnvoi().getMonthValue() == Integer.parseInt(mois));
            boolean matchPDF = !filterPDF || !c.getDocuments().isEmpty();
            boolean matchResponse = !filterResponse || c.getStatut() != StatutCandidature.EN_ATTENTE;
            return matchSearch && matchStatut && matchMonth && matchPDF && matchResponse;
        });
    }




//    private LocalDateTime extractDateFromPdf(File f) throws IOException {
//
//        try (PDDocument document = PDDocument.load(f)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            String text = stripper.getText(document);
//
//            Matcher matcher = dateTimePattern.matcher(text);
//            while (matcher.find()) {
//                try {
//                    return LocalDateTime.parse(matcher.group(1), formatter);
//                } catch (DateTimeParseException ignored) {}
//            }
//        }
//
//        return null;
//    }
//
//    private void refreshPdfList(Candidature c) {
//        var list = FXCollections.observableArrayList(c.getDocuments());
//        list.sort(Comparator.comparing(
//                d -> d.getDateMail() != null ? d.getDateMail() : LocalDateTime.MIN,
//                Comparator.reverseOrder()
//        ));
//        pdfViewerPane.setPdfList(list, c);
//    }


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

    private void renameAllFolders() {

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (Candidature c : filteredCandidatures) {
                    try {
                        FileSystemService.renameCandidatureFolderWithOldestPdfDate(c);
                    } catch (IOException e) {
                        System.err.println(
                                "Renommage impossible pour " +
                                        c.getEntreprise() + " - " + c.getPoste()
                        );
                        e.printStackTrace();
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            controller.save();
            table.refresh();
        });

        new Thread(task, "rename-candidature-folders").start();

    }

    public static void main(String[] args) { launch(args); }
}
