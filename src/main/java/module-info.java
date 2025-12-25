module app {

    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;


    requires java.desktop;
    requires static lombok;
    requires org.apache.pdfbox;
    requires javafx.swing;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // JavaFX FXML
    opens app to javafx.fxml;

    // OUVERTURE POUR JACKSON (OBLIGATOIRE)
    opens app.model to com.fasterxml.jackson.databind;

    // Exports normaux
    exports app;
    exports app.model;
}
