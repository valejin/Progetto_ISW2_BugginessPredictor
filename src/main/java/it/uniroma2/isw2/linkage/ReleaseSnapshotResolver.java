package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.Release;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReleaseSnapshotResolver {

    private static final DateTimeFormatter GIT_BEFORE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GitCommandExecutor git;

    public ReleaseSnapshotResolver(GitCommandExecutor git) {
        this.git = git;
    }

    public Map<Integer, String> resolveSnapshotCommits(List<Release> releases)
            throws IOException, InterruptedException {
        Map<Integer, String> result = new HashMap<>();

        for (Release r : releases) {
            // +1 giorno per includere tutti i commit della giornata stessa della release
            String beforeDate = r.getReleaseDate().plusDays(1).format(GIT_BEFORE_FORMAT);

            List<String> lines = git.execute(
                    "git", "rev-list", "-n", "1",
                    "--before=" + beforeDate,
                    "--first-parent",
                    "HEAD"
            );

            if (!lines.isEmpty()) {
                result.put(r.getId(), lines.get(0).trim());
            }
        }
        return result;
    }
}