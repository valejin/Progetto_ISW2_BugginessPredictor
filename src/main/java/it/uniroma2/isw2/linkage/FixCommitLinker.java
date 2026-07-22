package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.FixCommit;
import it.uniroma2.isw2.model.Ticket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixCommitLinker {

    private static final Pattern TICKET_ID_PATTERN =
            Pattern.compile("SYNCOPE-\\d+", Pattern.CASE_INSENSITIVE);

    private final GitCommandExecutor git;

    public FixCommitLinker(GitCommandExecutor git) {
        this.git = git;
    }

    /**
     * Scansiona l'unione delle ancestry dei commit di release forniti
     * (i tag ufficiali delle release selezionate), non solo master: alcune
     * release derivano da branch di manutenzione mai confluiti in master,
     * e i fix committati solo lì andrebbero altrimenti persi.
     */
    public List<FixCommit> findFixCommits(List<Ticket> tickets, List<String> releaseCommitHashes)
            throws IOException, InterruptedException {
        Set<String> knownTicketIds = new HashSet<>();
        for (Ticket t : tickets) {
            knownTicketIds.add(t.getId().toUpperCase(Locale.ROOT));
        }

        List<String> command = new ArrayList<>(List.of("git", "log", "--all", "--format=%H%x09%ct%x09%s"));
        command.addAll(releaseCommitHashes);

        List<String> logLines = git.execute(command.toArray(new String[0]));

        List<FixCommit> fixCommits = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (String line : logLines) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) {
                continue;
            }
            String commitHash = parts[0].trim();
            long epochSeconds = parseLongOrZero(parts[1].trim());
            String subject = parts[2];

            Matcher matcher = TICKET_ID_PATTERN.matcher(subject);
            while (matcher.find()) {
                String ticketId = matcher.group().toUpperCase(Locale.ROOT);
                if (!knownTicketIds.contains(ticketId)) {
                    continue;
                }
                if (seenPairs.add(ticketId + "#" + commitHash)) {
                    fixCommits.add(new FixCommit(ticketId, commitHash, epochSeconds));
                }
            }
        }
        return fixCommits;
    }

    private long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}