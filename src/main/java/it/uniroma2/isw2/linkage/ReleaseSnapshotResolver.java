package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.Release;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ReleaseSnapshotResolver {

    private static final Logger LOGGER = Logger.getLogger(ReleaseSnapshotResolver.class.getName());

    private final GitCommandExecutor git;

    public ReleaseSnapshotResolver(GitCommandExecutor git) {
        this.git = git;
    }

    /**
     * Risolve lo snapshot di ogni release tramite il TAG Git ufficiale
     * (es. "syncope-1.2.2"), non per data.
     *
     * Motivo: Syncope ha spesso sviluppato la major version successiva
     * su master mentre le patch release continuavano su branch di
     * manutenzione separati (es. 1_2_X). Cercare "l'ultimo commit prima
     * della data" su master prende in quei casi uno snapshot completamente
     * diverso (e più avanzato) da quello realmente rilasciato.
     */
    public Map<Integer, String> resolveSnapshotCommits(List<Release> releases)
            throws IOException, InterruptedException {
        Map<Integer, String> result = new HashMap<>();

        for (Release r : releases) {
            String tagName = "syncope-" + r.getName();
            List<String> lines = git.execute("git", "rev-list", "-n", "1", tagName);

            if (!lines.isEmpty()) {
                result.put(r.getId(), lines.get(0).trim());
            } else {
                LOGGER.warning("Tag non trovato per la release " + r.getName() + ": nessuno snapshot risolto.");
            }
        }
        return result;
    }
}