package app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentFile {

    private Path fichier;
    private DocumentType type;
    private LocalDateTime dateImport;
    private String nom;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentFile other)) return false;
        return Objects.equals(fichier, other.fichier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fichier);
    }

    @Override
    public String toString() {
        return type + " - " + nom; // ou juste nom si tu préfères
    }
}

