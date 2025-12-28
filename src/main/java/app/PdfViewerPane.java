package app;

import app.controller.MainController;
import app.model.Candidature;
import app.model.DocumentFile;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import lombok.Getter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PdfViewerPane extends BorderPane {

    private final MainController controller;
    private final AtomicLong renderVersion = new AtomicLong();

    private DocumentFile currentDocumentFile;
    private Path currentPdfPath;
    private Candidature currentCandidature;

    private final ImageView imageView = new ImageView();

    @Getter
    private final ListView<DocumentFile> pdfListView = new ListView<>();

    private int currentPage = 0;

    public PdfViewerPane(List<DocumentFile> pdfList, MainController controller) {
        this.controller = controller;

        /* =========================
           IMAGE VIEW (PDF)
           ========================= */
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        setCenter(imageView);

// ScrollPane pour le PDF
        ScrollPane pdfScrollPane = new ScrollPane(imageView);
        pdfScrollPane.setFitToWidth(true);
        pdfScrollPane.setFitToHeight(true);
        pdfScrollPane.setPannable(true); // permet de bouger le PDF avec la souris
        setCenter(pdfScrollPane);





        /* =========================
           LISTE DES PDFs (TOP)
           ========================= */
        pdfListView.setPrefHeight(200); // hauteur lisible
        pdfListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentFile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getDateMail()
                        + " | "
                        + item.getType()
                        + " - "
                        + item.getNom());
            }
        });

        pdfListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, doc) -> {
                    if (doc != null) {
                        currentDocumentFile = doc;
                        currentPdfPath = doc.getFichier();
                        currentPage = 0;
                        openPdf(currentPdfPath);
                    }
                });

        setTop(pdfListView);
        BorderPane.setMargin(pdfListView, new Insets(5));

        /* =========================
           NAVIGATION PAGES (BOTTOM)
           ========================= */
        Button prev = new Button("Précédent");
        Button next = new Button("Suivant");

        prev.setOnAction(e -> {
            if (currentPage > 0) {
                currentPage--;
                renderPage();
            }
        });

        next.setOnAction(e -> {
            currentPage++;
            renderPage();
        });

        ToolBar toolbar = new ToolBar(prev, next);
        setBottom(toolbar);
    }

    /* =========================
       MISE À JOUR LISTE PDF
       ========================= */
    public void setPdfList(List<DocumentFile> pdfList, Candidature c) {
        currentCandidature = c;
        closePdf();

        pdfList.sort(Comparator.comparing(
                DocumentFile::getDateMail,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        pdfListView.setItems(FXCollections.observableArrayList(pdfList));

        if (!pdfList.isEmpty()) {
            pdfListView.getSelectionModel().select(0);
        }
    }

    /* =========================
       OUVERTURE PDF
       ========================= */
    private synchronized void openPdf(Path path) {
        currentPdfPath = path;
        currentPage = 0;
        renderPage();
    }

    /* =========================
       RENDU PAGE (THREAD SAFE)
       ========================= */
    private void renderPage() {
        if (currentPdfPath == null) return;

        long version = renderVersion.incrementAndGet();

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                try (PDDocument doc = Loader.loadPDF(currentPdfPath.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    BufferedImage img = renderer.renderImageWithDPI(currentPage, 150);
                    return SwingFXUtils.toFXImage(img, null);
                }
            }
        };

        task.setOnSucceeded(e -> {
            if (renderVersion.get() == version) {
                imageView.setFitWidth(
                        getWidth() - 20
                );
                imageView.setImage(task.getValue());
            }
        });

        new Thread(task, "pdf-render-" + version).start();
    }

    /* =========================
       FERMETURE PDF
       ========================= */
    private void closePdf() {
        renderVersion.incrementAndGet();
        imageView.setImage(null);
    }
}
