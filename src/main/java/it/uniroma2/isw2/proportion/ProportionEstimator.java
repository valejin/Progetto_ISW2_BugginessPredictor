package it.uniroma2.isw2.proportion;

import it.uniroma2.isw2.model.Ticket;

import java.util.List;
import java.util.Optional;

public class ProportionEstimator {

    public double computeAverageProportion(List<Ticket> ticketsWithKnownIv) {
        double sum = 0;
        int count = 0;
        for (Ticket t : ticketsWithKnownIv) {
            Optional<Double> proportion = computeProportion(t);
            if (proportion.isPresent()) {
                sum += proportion.get();
                count++;
            }
        }
        if (count == 0) {
            throw new IllegalStateException("Nessun ticket disponibile per calcolare Proportion");
        }
        return sum / count;
    }

    /** Proportion di un singolo ticket, vuoto se non calcolabile (dati mancanti o FV == OV). */
    private Optional<Double> computeProportion(Ticket t) {
        if (t.getFixVersion() == null || t.getOpeningVersion() == null || t.getInjectedVersion() == null) {
            return Optional.empty();
        }
        int fv = t.getFixVersion();
        int ov = t.getOpeningVersion();
        int iv = t.getInjectedVersion();
        if (fv == ov) {
            return Optional.empty(); // P non definito quando FV == OV
        }
        return Optional.of((double) (fv - iv) / (fv - ov));
    }

    public int estimateIv(Ticket ticket, double averageProportion) {
        int fv = ticket.getFixVersion();
        int ov = ticket.getOpeningVersion();
        int estimated = (int) Math.round(fv - (fv - ov) * averageProportion);
        return Math.max(1, Math.min(estimated, ov)); // vincolata a [1, OV]
    }
}