package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.BuggyClassEntry;
import it.uniroma2.isw2.model.FixCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuggyClassExtractor {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+\\d+(?:,\\d+)?\\s+@@.*$");

    private final GitCommandExecutor git;

    public BuggyClassExtractor(GitCommandExecutor git) {
        this.git = git;
    }

    public List<BuggyClassEntry> extract(List<FixCommit> fixCommits) throws IOException, InterruptedException {
        List<BuggyClassEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (FixCommit fix : fixCommits) {
            String parentHash = findParent(fix.getCommitHash());
            if (parentHash == null) {
                continue; // commit senza parent (es. commit iniziale del repo)
            }

            Map<String, Boolean> filesModified = diffProductionJavaFiles(parentHash, fix.getCommitHash());
            for (Map.Entry<String, Boolean> entry : filesModified.entrySet()) {
                // Boolean.TRUE.equals(...) invece di !entry.getValue(): unboxing null-safe,
                // il confronto avviene su un boolean primitivo invece che sull'oggetto boxed.
                boolean containsRemoval = Boolean.TRUE.equals(entry.getValue());
                if (!containsRemoval) {
                    continue; // solo aggiunte pure: nessun codice preesistente coinvolto
                }
                String key = fix.getTicketId() + "#" + entry.getKey();
                if (seen.add(key)) {
                    result.add(new BuggyClassEntry(fix.getTicketId(), fix.getCommitHash(), entry.getKey()));
                }
            }
        }
        return result;
    }

    private String findParent(String commitHash) throws IOException, InterruptedException {
        List<String> lines = git.execute("git", "rev-list", "--parents", "-n", "1", commitHash);
        if (lines.isEmpty()) {
            return null;
        }
        String[] parts = lines.get(0).trim().split("\\s+");
        return parts.length >= 2 ? parts[1] : null;
    }

    // Ritorna path .java di produzione -> "contiene almeno un hunk con oldCount > 0"
    private Map<String, Boolean> diffProductionJavaFiles(String parentHash, String fixHash)
            throws IOException, InterruptedException {
        List<String> diffLines = git.execute("git", "diff", "-U0", "-M", parentHash, fixHash, "--");
        Map<String, Boolean> result = new LinkedHashMap<>();
        String currentFile = null;

        for (String line : diffLines) {
            if (line.startsWith("diff --git")) {
                currentFile = resolveDiffHeaderFile(line, result);
            } else if (currentFile != null) {
                recordHunkIfRemovesLines(line, currentFile, result);
            }
        }
        return result;
    }

    /**
     * Se la riga e' un header "diff --git" che introduce un file .java di produzione, lo
     * registra in result (se non gia' presente) e ne ritorna il path; altrimenti ritorna null
     * (nessun file "corrente" attivo, le righe successive vengono ignorate finche' non arriva
     * un nuovo header pertinente).
     */
    private String resolveDiffHeaderFile(String diffGitHeaderLine, Map<String, Boolean> result) {
        String newPath = extractNewPath(diffGitHeaderLine);
        if (newPath != null && newPath.endsWith(".java") && isProductionClass(newPath)) {
            result.putIfAbsent(newPath, false);
            return newPath;
        }
        return null;
    }

    /** Se la riga e' un header di hunk con almeno una riga rimossa (oldCount > 0), marca currentFile come modificato. */
    private void recordHunkIfRemovesLines(String line, String currentFile, Map<String, Boolean> result) {
        Matcher m = HUNK_HEADER.matcher(line);
        if (m.matches()) {
            int oldCount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            if (oldCount > 0) {
                result.put(currentFile, true);
            }
        }
    }

    private String extractNewPath(String diffGitHeaderLine) {
        int bIndex = diffGitHeaderLine.indexOf(" b/");
        return bIndex == -1 ? null : diffGitHeaderLine.substring(bIndex + 3).trim();
    }

    private boolean isProductionClass(String path) {
        String normalized = path.replace("\\", "/");
        return normalized.contains("/src/main/java/")
                && !normalized.contains("/src/test/java/")
                && !normalized.startsWith("fit/")
                && !normalized.contains("/fit/");
    }
}