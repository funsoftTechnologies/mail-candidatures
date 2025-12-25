package app;

import app.controller.MainController;
import app.model.DocumentFile;
import app.model.DocumentType;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import lombok.Getter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;

public class PdfViewerPane extends BorderPane {

    private final MainController controller;

    private DocumentFile currentDocumentFile;

    private final ImageView imageView;
    private PDDocument currentDocument;
    private PDFRenderer renderer;
    private int currentPage;
    private Task<Image> currentRenderTask;

    /* =========================
       Accès externe ComboBox
       ========================= */
    @Getter
    private final ComboBox<DocumentFile> pdfSelector;

    public PdfViewerPane(List<DocumentFile> pdfList, MainController controller) {
        this.controller = controller;

        /* =========================
           ImageView (PDF)
           ========================= */
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        setCenter(imageView);

        /* =========================
           ComboBox PDF
           ========================= */
        pdfSelector = new ComboBox<>();
        pdfSelector.getItems().addAll(pdfList);

        pdfSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentFile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getType() + " - " + item.getNom());
            }
        });
        pdfSelector.setButtonCell(pdfSelector.getCellFactory().call(null));

        pdfSelector.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, doc) -> {
                    if (doc != null) {
                        currentDocumentFile = doc;
                        openPdf(doc.getFichier());
                    }
                });

        HBox topBar = new HBox(10, pdfSelector);
        topBar.setPadding(new Insets(5));
        setTop(topBar);

        /* =========================
           Navigation pages
           ========================= */
        Button prev = new Button("Précédent");
        Button next = new Button("Suivant");

        prev.setOnAction(e -> {
            if (currentDocument != null && currentPage > 0) {
                currentPage--;
                renderPage();
            }
        });

        next.setOnAction(e -> {
            if (currentDocument != null
                    && currentPage < currentDocument.getNumberOfPages() - 1) {
                currentPage++;
                renderPage();
            }
        });

        HBox pageControls = new HBox(10, prev, next);
        pageControls.setPadding(new Insets(5));
        setBottom(pageControls);

        /* =========================
           Navigation clavier
           ========================= */
        setOnKeyPressed(event -> {
            int index = pdfSelector.getSelectionModel().getSelectedIndex();
            switch (event.getCode()) {
                case RIGHT -> {
                    if (index < pdfSelector.getItems().size() - 1)
                        pdfSelector.getSelectionModel().select(index + 1);
                }
                case LEFT -> {
                    if (index > 0)
                        pdfSelector.getSelectionModel().select(index - 1);
                }
            }
        });

        /* =========================
           Navigation molette
           ========================= */
        setOnScroll(event -> {
            int index = pdfSelector.getSelectionModel().getSelectedIndex();
            if (event.getDeltaY() < 0 && index < pdfSelector.getItems().size() - 1) {
                pdfSelector.getSelectionModel().select(index + 1);
            } else if (event.getDeltaY() > 0 && index > 0) {
                pdfSelector.getSelectionModel().select(index - 1);
            }
        });

        /* =========================
           Menu contextuel PDF
           ========================= */
        ContextMenu contextMenu = new ContextMenu();

        MenuItem markEnvoi = new MenuItem("Marquer comme ENVOI");
        MenuItem markReponse = new MenuItem("Marquer comme RÉPONSE");
        MenuItem deletePdf = new MenuItem("Supprimer le PDF");

        contextMenu.getItems().addAll(
                markEnvoi,
                markReponse,
                new SeparatorMenuItem(),
                deletePdf
        );

        imageView.setOnContextMenuRequested(e -> {
            if (!contextMenu.isShowing()) {
                contextMenu.show(imageView, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });

        // Fermer le menu si clic en dehors
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                    if (contextMenu.isShowing()) {
                        // Récupère le nœud sur lequel le menu est ouvert
                        Node owner = contextMenu.getOwnerNode();

                        // Si le clic est en dehors du menu contextuel
                        if (!contextMenu.getSkin().getNode().localToScene(contextMenu.getSkin().getNode().getBoundsInLocal())
                                .contains(event.getSceneX(), event.getSceneY())) {
                            contextMenu.hide();
                        }
                    }
                });
            }
        });

        markEnvoi.setOnAction(e -> updateType(DocumentType.ENVOI));
        markReponse.setOnAction(e -> updateType(DocumentType.REPONSE));
        deletePdf.setOnAction(e -> deleteCurrentPdf());

        contextMenu.setOnShowing(e -> {
            if (currentDocumentFile == null) {
                markEnvoi.setDisable(true);
                markReponse.setDisable(true);
                deletePdf.setDisable(true);
            } else {
                markEnvoi.setDisable(currentDocumentFile.getType() == DocumentType.ENVOI);
                markReponse.setDisable(currentDocumentFile.getType() == DocumentType.REPONSE);
                deletePdf.setDisable(false);
            }
        });

        /* =========================
           Sélection initiale
           ========================= */
        if (!pdfList.isEmpty()) {
            pdfSelector.getSelectionModel().select(0);
        }
    }

    /* =========================
       Modification type
       ========================= */
    private void updateType(DocumentType newType) {
        if (currentDocumentFile == null) return;

        currentDocumentFile.setType(newType);

        // Forcer rafraîchissement du ComboBox
        DocumentFile selected = pdfSelector.getValue();
        pdfSelector.setValue(null);
        pdfSelector.setValue(selected);

        // Sauvegarde dans le JSON
        if (controller != null) {
            controller.save();
        }
    }

    /* =========================
       Suppression PDF
       ========================= */
    private void deleteCurrentPdf() {
        if (currentDocumentFile == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression");
        confirm.setHeaderText("Supprimer ce PDF ?");
        confirm.setContentText(currentDocumentFile.getNom());

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {

                int index = pdfSelector.getSelectionModel().getSelectedIndex();
                pdfSelector.getItems().remove(currentDocumentFile);

                if (pdfSelector.getItems().isEmpty()) {
                    closePdf();
                    currentDocumentFile = null;
                } else {
                    pdfSelector.getSelectionModel()
                            .select(Math.min(index, pdfSelector.getItems().size() - 1));
                }
            }
        });
    }

    /* =========================
       Chargement PDF
       ========================= */
    private synchronized void openPdf(java.nio.file.Path path) {
        // Annuler rendu en cours
        if (currentRenderTask != null && currentRenderTask.isRunning()) {
            currentRenderTask.cancel();
        }

        // On ferme le document précédent **après la fin du rendu** pour éviter les exceptions
        if (currentDocument != null) {
            try { currentDocument.close(); } catch (IOException ignored) {}
        }

        try {
            currentDocument = Loader.loadPDF(path.toFile());
            renderer = new PDFRenderer(currentDocument);
            currentPage = 0;
            renderPage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       Rendu page (thread-safe)
       ========================= */
    private synchronized void renderPage() {
        if (currentDocument == null) return;

        // Annuler le rendu en cours si existant
        if (currentRenderTask != null && currentRenderTask.isRunning()) {
            currentRenderTask.cancel();
        }

        // Nouveau rendu
        currentRenderTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                return SwingFXUtils.toFXImage(renderer.renderImageWithDPI(currentPage, 150), null);
            }
        };

        currentRenderTask.setOnSucceeded(e -> {
            if (!currentRenderTask.isCancelled()) {
                double screenHeight = javafx.stage.Screen.getPrimary().getBounds().getHeight();
                imageView.setFitHeight(screenHeight);
                imageView.setImage(currentRenderTask.getValue());
            }
        });

        currentRenderTask.setOnFailed(e -> {
            Throwable ex = currentRenderTask.getException();
            if (!(ex instanceof CancellationException)) {
                ex.printStackTrace();
            }
        });

        new Thread(currentRenderTask).start();
    }


    /* =========================
       Fermeture PDF
       ========================= */

    public synchronized void closePdf() {
        if (currentRenderTask != null && currentRenderTask.isRunning()) {
            currentRenderTask.cancel();
        }
        if (currentDocument != null) {
            try {
                currentDocument.close();
            } catch (IOException ignored) {}
            currentDocument = null;
            renderer = null;
            imageView.setImage(null);
        }
    }

    /* =========================
   Mise à jour de la liste PDF
   ========================= */
    public void setPdfList(List<DocumentFile> pdfList) {
        // Trier du plus récent au plus ancien
        pdfList.sort((d1, d2) -> d2.getDateImport().compareTo(d1.getDateImport()));

        pdfSelector.getItems().setAll(pdfList);
        if (!pdfList.isEmpty()) {
            pdfSelector.getSelectionModel().select(0);
        } else {
            closePdf();
            currentDocumentFile = null;
        }
    }


}
