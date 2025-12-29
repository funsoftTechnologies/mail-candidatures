package app.service;

import app.model.Candidature;
import app.model.StatutCandidature;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class CandidatureService {

    // Accès à toutes les candidatures
    private List<Candidature> candidatures;

    public CandidatureService(List<Candidature> candidatures) {
        this.candidatures = candidatures;
    }

    // Nombre total de candidatures
    public int getTotal() {
        return candidatures.size();
    }

    // Nombre de candidatures par statut
    public long countByStatut(StatutCandidature statut) {
        return candidatures.stream()
                .filter(c -> c.getStatut() == statut)
                .count();
    }

    // Nombre de candidatures par entreprise
    public Map<String, Long> countByEntreprise() {
        return candidatures.stream()
                .collect(Collectors.groupingBy(Candidature::getEntreprise, Collectors.counting()));
    }

    // Nombre de candidatures par mois
    public Map<YearMonth, Long> countByMonth() {
        return candidatures.stream()
                .collect(Collectors.groupingBy(
                        c -> YearMonth.from(c.getDateEnvoi()),
                        Collectors.counting()
                ));
    }

}
