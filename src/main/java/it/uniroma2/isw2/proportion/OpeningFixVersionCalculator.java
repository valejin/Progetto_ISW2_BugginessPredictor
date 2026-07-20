package it.uniroma2.isw2.proportion;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public class OpeningFixVersionCalculator {

    private final List<Release> allReleasesSortedById;

    public OpeningFixVersionCalculator(List<Release> allReleasesSortedById) {
        this.allReleasesSortedById = allReleasesSortedById;
    }

    public void computeOpeningAndFixVersion(Ticket ticket) {
        ticket.setOpeningVersion(findOpeningVersion(ticket.getCreationDate()));
        ticket.setFixVersion(findFixVersion(ticket.getResolutionDate()));
    }

    // OV = ultima release rilasciata al momento (o prima) della data di apertura del ticket.
    // Se il ticket è precedente alla primissima release nota, ritorna null:
    // il ticket verrà escluso a monte in Main
    private Integer findOpeningVersion(LocalDateTime creationDate) {
        Integer ov = null;
        for (Release r : allReleasesSortedById) {
            if (!r.getReleaseDate().isAfter(creationDate)) {
                ov = r.getId();
            } else {
                break;
            }
        }
        return ov;
    }

    // FV = prima release uscita dopo (o alla) data di risoluzione del ticket
    private Integer findFixVersion(LocalDateTime resolutionDate) {
        for (Release r : allReleasesSortedById) {
            if (!r.getReleaseDate().isBefore(resolutionDate)) {
                return r.getId();
            }
        }
        return null; // risolto dopo l'ultima release nota: nessuna release contiene ancora il fix
    }
}