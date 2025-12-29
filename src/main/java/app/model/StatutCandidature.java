package app.model;

import lombok.Getter;

@Getter
public enum StatutCandidature {

    TOUTES("Toutes"),
    EN_ATTENTE("En attente"),
    REFUS("Non retenu"),
    ENTRETIEN("Entretien");


    private final String label;

    StatutCandidature(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
