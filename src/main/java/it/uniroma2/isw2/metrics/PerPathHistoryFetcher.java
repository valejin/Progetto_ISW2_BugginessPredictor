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

    /** Riga di header "COMMIT_START\t<hash>\t<epoch>\t<author>" gia' spacchettata. */
    private record CommitHeader(String hash, long epoch, String author) {
    }

    public List<FileTouch> fetchHistoryUntilRelease(String releaseCommitHash, String classPath)
            throws IOException, InterruptedException {
        List<String> lines = git.execute(
                "git", "log", "--follow", "--numstat",
                "--format=" + HEADER_PREFIX + "%H%x09%ct%x09%ae",
                releaseCommitHash, "--", classPath
        );

        List<FileTouch> touches = new ArrayList<>();
        CommitHeader currentCommit = null;

        for (String line : lines) {
            if (line.startsWith(HEADER_PREFIX)) {
                currentCommit = parseHeader(line);
            } else if (currentCommit != null && currentCommit.hash() != null) {
                appendTouchIfNumstatLine(line, currentCommit, touches);
            }
        }
        return touches;
    }

    private CommitHeader parseHeader(String line) {
        String[] parts = line.substring(HEADER_PREFIX.length()).split("\t", 3);
        String hash = parts.length > 0 ? parts[0] : null;
        long epoch = parts.length > 1 ? parseLongOrZero(parts[1]) : 0;
        String author = parts.length > 2 ? parts[2] : "";
        return new CommitHeader(hash, epoch, author);
    }

    /** Riga "numstat" (added\tdeleted\tpath) del commit corrente: aggiunge un FileTouch se ben formata. */
    private void appendTouchIfNumstatLine(String line, CommitHeader currentCommit, List<FileTouch> touches) {
        if (line.isBlank()) {
            return;
        }
        String[] parts = line.split("\t", 3);
        if (parts.length == 3) {
            int added = parseIntOrZero(parts[0]);
            int deleted = parseIntOrZero(parts[1]);
            touches.add(new FileTouch(currentCommit.hash(), currentCommit.epoch(), currentCommit.author(),
                    added, deleted));
        }
    }

    private int parseIntOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private long parseLongOrZero(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }
}