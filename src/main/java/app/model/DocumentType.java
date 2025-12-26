package app.model;

public enum DocumentType {
    ENVOI("envoi"),
    REPONSE("reponse");

    private final String folder;

    DocumentType(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }
}
