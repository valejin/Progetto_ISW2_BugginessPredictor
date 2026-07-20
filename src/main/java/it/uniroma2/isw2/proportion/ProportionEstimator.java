package it.uniroma2.isw2.proportion;

import it.uniroma2.isw2.model.Ticket;

import java.util.List;

public class ProportionEstimator {

    public double computeAverageProportion(List<Ticket> ticketsWithKnownIv) {
        double sum = 0;
        int count = 0;
        for (Ticket t : ticketsWithKnownIv) {
            if (t.getFixVersion() == null || t.getOpeningVersion() == null || t.getInjectedVersion() == null) {
                continue;
            }
            int fv = t.getFixVersion();
            int ov = t.getOpeningVersion();
            int iv = t.getInjectedVersion();
            if (fv == ov) {
                continue; // P non definito quando FV == OV
            }
            sum += (double) (fv - iv) / (fv - ov);
            count++;
        }
        if (count == 0) {
            throw new IllegalStateException("Nessun ticket disponibile per calcolare Proportion");
        }
        return sum / count;
    }

    public int estimateIv(Ticket ticket, double averageProportion) {
        int fv = ticket.getFixVersion();
        int ov = ticket.getOpeningVersion();
        int estimated = (int) Math.round(fv - (fv - ov) * averageProportion);
        return Math.max(1, Math.min(estimated, ov)); // vincolata a [1, OV]
    }
}