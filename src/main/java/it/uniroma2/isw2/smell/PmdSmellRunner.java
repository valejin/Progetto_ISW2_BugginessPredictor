package it.uniroma2.isw2.smell;

import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.util.CsvLineParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PmdSmellRunner {

    private static final String RULESET = "rulesets/java/quickstart.xml";

    private final GitCommandExecutor git;
    private final String pmdExecutablePath;
    private final Path workDir;

    public PmdSmellRunner(GitCommandExecutor git, String pmdExecutablePath, Path workDir) {
        this.git = git;
        this.pmdExecutablePath = pmdExecutablePath;
        this.workDir = workDir.toAbsolutePath();
    }

    /**
     * Crea un worktree Git temporaneo allo snapshot della release, esegue PMD
     * solo sulle classi di produzione note per quella release, e ritorna il
     * conteggio violazioni per classe. Il worktree viene rimosso alla fine.
     */
    public Map<String, Integer> countSmellsForRelease(int releaseId, String commitHash,
                                                      List<String> classPaths)
            throws IOException, InterruptedException {
        Files.createDirectories(workDir);
        Path worktreePath = workDir.resolve("worktree-release-" + releaseId);

        git.execute("git", "worktree", "add", "--force", worktreePath.toString(), commitHash);

        try {
            Path fileListPath = workDir.resolve("filelist-" + releaseId + ".txt");
            try (FileWriter writer = new FileWriter(fileListPath.toFile())) {
                for (String classPath : classPaths) {
                    writer.append(worktreePath.resolve(classPath).toString()).append("\n");
                }
            }

            Path reportPath = workDir.resolve("pmd-report-" + releaseId + ".csv");
            runPmd(fileListPath, reportPath);

            return parseReport(reportPath, worktreePath);
        } finally {
            git.execute("git", "worktree", "remove", "--force", worktreePath.toString());
        }
    }

    /**
     * Risolve il path assoluto di cmd.exe tramite la variabile d'ambiente SystemRoot
     * (impostata dal sistema operativo, non manipolabile come il PATH) invece di lanciare
     * "cmd.exe" per nome: evita che l'OS lo cerchi nelle directory del PATH, dove una
     * directory scrivibile da terzi potrebbe contenere un eseguibile malevolo con lo stesso
     * nome (rule java:S4036).
     */
    private static String resolveCmdExePath() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            throw new IllegalStateException(
                    "Variabile d'ambiente SystemRoot non impostata: impossibile risolvere cmd.exe");
        }
        return Path.of(systemRoot, "System32", "cmd.exe").toString();
    }

    private void runPmd(Path fileListPath, Path reportPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                resolveCmdExePath(), "/c", pmdExecutablePath, "check",
                "--file-list", fileListPath.toString(),
                "-R", RULESET,
                "-f", "csv",
                "-r", reportPath.toString(),
                "--no-cache",
                "--no-fail-on-violation",
                "--no-fail-on-error"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("PMD terminato con exit code " + exitCode
                    + ". Output:\n" + String.join("\n", output));
        }
    }

    private Map<String, Integer> parseReport(Path reportPath, Path worktreePath) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        if (!Files.exists(reportPath)) {
            return counts;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(reportPath.toFile()))) {
            String header = reader.readLine(); // "Problem","Package","File","Priority","Line","Description","Rule set","Rule"
            if (header == null) {
                return counts;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                processReportLine(line, worktreePath, counts);
            }
        }
        return counts;
    }

    /** Analizza una riga del CSV di PMD e, se ben formata, incrementa il conteggio smell del file corrispondente. */
    private void processReportLine(String line, Path worktreePath, Map<String, Integer> counts) {
        if (line.isBlank()) {
            return;
        }
        String[] fields = splitCsvLine(line);
        if (fields.length < 3) {
            return;
        }
        String absoluteFile = fields[2];
        String relativePath = worktreePath.relativize(Path.of(absoluteFile))
                .toString().replace("\\", "/");
        counts.merge(relativePath, 1, Integer::sum);
    }

    // Split che rispetta le virgolette: implementazione centralizzata in CsvLineParser
    // (era duplicata identica in piu' classi, generando duplicazione rilevata da SonarCloud).
    private String[] splitCsvLine(String line) {
        return CsvLineParser.splitCsvLine(line);
    }
}