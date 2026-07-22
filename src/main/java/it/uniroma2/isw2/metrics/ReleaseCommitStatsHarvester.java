package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.model.CommitStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per una release: raccoglie, per ogni commit raggiungibile dal suo tag
 * seguendo --first-parent, quanti file/directory/sottosistemi ha toccato
 * quel commit nella sua interezza. Ancorato al tag, non a master: segue
 * la vera discendenza di quella release anche se e' un branch di
 * manutenzione mai confluito in master.
 */
public class ReleaseCommitStatsHarvester {

    private static final String HEADER_PREFIX = "COMMIT_START\t";

    private final GitCommandExecutor git;

    public ReleaseCommitStatsHarvester(GitCommandExecutor git) {
        this.git = git;
    }

    public Map<String, CommitStats> harvestForRelease(String releaseCommitHash)
            throws IOException, InterruptedException {
        List<String> lines = git.execute(
                "git", "log", "--numstat",
                "--format=" + HEADER_PREFIX + "%H",
                releaseCommitHash
        );

        Map<String, CommitStats> result = new HashMap<>();
        String currentHash = null;
        List<String> currentPaths = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith(HEADER_PREFIX)) {
                flush(result, currentHash, currentPaths);
                currentHash = line.substring(HEADER_PREFIX.length()).trim();
                currentPaths = new ArrayList<>();
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                currentPaths.add(parts[2]);
            }
        }
        flush(result, currentHash, currentPaths);

        return result;
    }

    private void flush(Map<String, CommitStats> result, String commitHash, List<String> paths) {
        if (commitHash == null || paths.isEmpty()) {
            return;
        }
        Set<String> directories = new HashSet<>();
        Set<String> subsystems = new HashSet<>();
        for (String p : paths) {
            directories.add(directoryOf(p));
            subsystems.add(subsystemOf(p));
        }
        result.put(commitHash, new CommitStats(paths.size(), directories.size(), subsystems.size()));
    }

    private String directoryOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx == -1 ? "" : path.substring(0, idx);
    }

    private String subsystemOf(String path) {
        int idx = path.indexOf('/');
        return idx == -1 ? path : path.substring(0, idx);
    }
}