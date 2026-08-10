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

        Map<String, Set<String>> classesByTicket = buildClassesByTicket(buggyClassEntries);
        Map<String, Boolean> labels = initializeLabels(inventory);

        for (Ticket t : tickets) {
            markBuggyClassesForTicket(t, classesByTicket, labels);
        }
        return labels;
    }

    private Map<String, Set<String>> buildClassesByTicket(List<BuggyClassEntry> buggyClassEntries) {
        Map<String, Set<String>> classesByTicket = new HashMap<>();
        for (BuggyClassEntry entry : buggyClassEntries) {
            classesByTicket
                    .computeIfAbsent(entry.getTicketId(), k -> new HashSet<>())
                    .add(entry.getClassPath());
        }
        return classesByTicket;
    }

    private Map<String, Boolean> initializeLabels(List<ReleaseClassEntry> inventory) {
        Map<String, Boolean> labels = new HashMap<>();
        for (ReleaseClassEntry rc : inventory) {
            labels.put(key(rc.getReleaseId(), rc.getClassPath()), false);
        }
        return labels;
    }

    /**
     * Etichetta come buggy le coppie (release, classe) coperte dalla finestra [IV, FV) di un
     * singolo ticket. Un ticket senza IV/FV nota, o senza classi buggy collegate, non etichetta
     * nulla: gestito con un "return" anticipato dentro questo metodo (un solo punto di uscita
     * per condizione) invece di piu' "continue" nel loop esterno di labelClasses, cosi' quel
     * loop resta con al piu' un break/continue.
     */
    private void markBuggyClassesForTicket(Ticket t, Map<String, Set<String>> classesByTicket,
                                           Map<String, Boolean> labels) {
        if (t.getInjectedVersion() == null || t.getFixVersion() == null) {
            return;
        }
        Set<String> buggyClasses = classesByTicket.get(t.getId());
        if (buggyClasses == null || buggyClasses.isEmpty()) {
            return; // ticket senza fix commit collegato: nessuna classe da etichettare
        }
        for (int releaseId = t.getInjectedVersion(); releaseId < t.getFixVersion(); releaseId++) {
            for (String classPath : buggyClasses) {
                String k = key(releaseId, classPath);
                labels.computeIfPresent(k, (mapKey, currentValue) -> true);
            }
        }
    }

    private String key(int releaseId, String classPath) {
        return releaseId + "#" + classPath;
    }
}