package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.model.FileTouch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Storia di un singolo file (segue i rename con --follow), dall'inizio
 * del progetto fino al commit della release incluso. Ancorata al tag,
 * quindi corretta anche quando la release proviene da un branch di
 * manutenzione diverso da master.
 */
public class PerPathHistoryFetcher {

    private static final String HEADER_PREFIX = "COMMIT_START\t";

    private final GitCommandExecutor git;

    public PerPathHistoryFetcher(GitCommandExecutor git) {
        this.git = git;
    }

    public List<FileTouch> fetchHistoryUntilRelease(String releaseCommitHash, String classPath)
            throws IOException, InterruptedException {
        List<String> lines = git.execute(
                "git", "log", "--follow", "--numstat",
                "--format=" + HEADER_PREFIX + "%H%x09%ct%x09%ae",
                releaseCommitHash, "--", classPath
        );

        List<FileTouch> touches = new ArrayList<>();
        String currentHash = null;
        long currentEpoch = 0;
        String currentAuthor = null;

        for (String line : lines) {
            if (line.startsWith(HEADER_PREFIX)) {
                String[] parts = line.substring(HEADER_PREFIX.length()).split("\t", 3);
                currentHash = parts.length > 0 ? parts[0] : null;
                currentEpoch = parts.length > 1 ? parseLongOrZero(parts[1]) : 0;
                currentAuthor = parts.length > 2 ? parts[2] : "";
                continue;
            }
            if (line.isBlank() || currentHash == null) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                int added = parseIntOrZero(parts[0]);
                int deleted = parseIntOrZero(parts[1]);
                touches.add(new FileTouch(currentHash, currentEpoch, currentAuthor, added, deleted));
            }
        }
        return touches;
    }

    private int parseIntOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private long parseLongOrZero(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }
}