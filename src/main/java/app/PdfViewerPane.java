package app;

import app.controller.MainController;
import app.model.Candidature;
import app.model.DocumentFile;
import app.model.DocumentType;
import javafx.collections.FXCollections;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PdfViewerPane extends BorderPane {

    private final MainController controller;
    private final AtomicLong renderVersion = new AtomicLong();

    private DocumentFile currentDocumentFile;
    private Path currentPdfPath;

    private final ImageView imageView;
    private PDDocument currentDocument;
    private PDFRenderer renderer;
    private int currentPage = 0;
    private Task<Image> currentRenderTask;
    private Candidature currentCandidature;

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
                        currentPdfPath = doc.getFichier();
                        currentPage = 0;

                        openPdf(currentPdfPath); // <<-- il faut appeler openPdf pour charger le PDF
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

        // Sauvegarde dans le JSON
        if (controller != null) {
            controller.save();
        }

        // Forcer le rafraîchissement du ComboBox
        pdfSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentFile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getType() + " - " + item.getNom());
            }
        });
        pdfSelector.setButtonCell(pdfSelector.getCellFactory().call(null));

        // Re-sélectionner l’élément courant pour mettre à jour l’affichage
        pdfSelector.getSelectionModel().select(currentDocumentFile);
    }



    /* =========================
       Suppression PDF
       ========================= */
    private void deleteCurrentPdf() {
        if (currentDocumentFile == null || currentCandidature == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression");
        confirm.setHeaderText("Supprimer ce PDF ?");
        confirm.setContentText(currentDocumentFile.getNom());

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                int index = pdfSelector.getSelectionModel().getSelectedIndex();

                // Supprimer du disque
                currentDocumentFile.getFichier().toFile().delete();

                // Supprimer du modèle
                currentCandidature.getDocuments().remove(currentDocumentFile);

                // Supprimer de l'UI
                pdfSelector.getItems().remove(currentDocumentFile);

                // Mettre à jour currentDocumentFile
                if (pdfSelector.getItems().isEmpty()) {
                    currentDocumentFile = null;
                    closePdf();
                } else {
                    // Sélectionner le PDF suivant ou le dernier si on a supprimé le dernier
                    int newIndex = Math.min(index, pdfSelector.getItems().size() - 1);
                    pdfSelector.getSelectionModel().select(newIndex);
                }

                // Sauvegarder dans JSON
                controller.save();
            }
        });
    }



    /* =========================
       Chargement PDF
       ========================= */
    private synchronized void openPdf(Path path) {
        currentPdfPath = path;
        currentPage = 0;
        renderPage();
    }


    /* =========================
       Rendu page (thread-safe)
       ========================= */
    private void renderPage() {
        if (currentPdfPath == null) return;

        final long myVersion = renderVersion.incrementAndGet();
        final Path pdfPath = currentPdfPath;
        final int pageToRender = currentPage;

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    BufferedImage img = renderer.renderImageWithDPI(pageToRender, 150);
                    return SwingFXUtils.toFXImage(img, null);
                }
            }
        };

        task.setOnSucceeded(e -> {
            // Un rendu plus récent existe → on ignore
            if (renderVersion.get() != myVersion) return;

            imageView.setFitHeight(
                    javafx.stage.Screen.getPrimary().getBounds().getHeight()
            );
            imageView.setImage(task.getValue());
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
        });

        new Thread(task, "pdf-render-" + myVersion).start();
    }


    /* =========================
       Fermeture PDF
       ========================= */
    public synchronized void closePdf() {
        renderVersion.incrementAndGet(); // invalide les rendus en cours
        imageView.setImage(null);
    }


    /* =========================
   Mise à jour de la liste PDF
   ========================= */
    public void setPdfList(List<DocumentFile> pdfList, Candidature c) {
        currentCandidature = c;

        // Fermer le PDF précédent
        closePdf();
        currentDocumentFile = null;

        // Mettre à jour la liste de la ComboBox
        pdfSelector.getItems().setAll(FXCollections.observableArrayList(pdfList));

        // Forcer sélection et refresh
        if (!pdfList.isEmpty()) {
            pdfSelector.getSelectionModel().select(0);
            pdfSelector.setValue(pdfList.get(0)); // force refresh
        }
    }

}
