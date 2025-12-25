package app.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
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
}

