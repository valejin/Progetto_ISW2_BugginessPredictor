package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.BuggyClassEntry;
import it.uniroma2.isw2.model.ReleaseClassEntry;
import it.uniroma2.isw2.model.Ticket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BugLabeler {

    public Map<String, Boolean> labelClasses(List<ReleaseClassEntry> inventory,
                                             List<Ticket> tickets,
                                             List<BuggyClassEntry> buggyClassEntries) {

        Map<String, Set<String>> classesByTicket = new HashMap<>();
        for (BuggyClassEntry entry : buggyClassEntries) {
            classesByTicket
                    .computeIfAbsent(entry.getTicketId(), k -> new HashSet<>())
                    .add(entry.getClassPath());
        }

        Map<String, Boolean> labels = new HashMap<>();
        for (ReleaseClassEntry rc : inventory) {
            labels.put(key(rc.getReleaseId(), rc.getClassPath()), false);
        }

        for (Ticket t : tickets) {
            if (t.getInjectedVersion() == null || t.getFixVersion() == null) {
                continue;
            }
            Set<String> buggyClasses = classesByTicket.get(t.getId());
            if (buggyClasses == null || buggyClasses.isEmpty()) {
                continue; // ticket senza fix commit collegato: nessuna classe da etichettare
            }
            for (int releaseId = t.getInjectedVersion(); releaseId < t.getFixVersion(); releaseId++) {
                for (String classPath : buggyClasses) {
                    String k = key(releaseId, classPath);
                    if (labels.containsKey(k)) {
                        labels.put(k, true);
                    }
                }
            }
        }
        return labels;
    }

    private String key(int releaseId, String classPath) {
        return releaseId + "#" + classPath;
    }
}