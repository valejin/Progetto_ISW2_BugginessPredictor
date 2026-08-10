package it.uniroma2.isw2;

import it.uniroma2.isw2.acquisition.ReleaseCsvReader;
import it.uniroma2.isw2.dataset.DatasetCsvReader;
import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.linkage.ReleaseSnapshotResolver;
import it.uniroma2.isw2.metrics.PerPathHistoryFetcher;
import it.uniroma2.isw2.metrics.ProcessMetricsCalculator;
import it.uniroma2.isw2.metrics.ReleaseCommitStatsHarvester;
import it.uniroma2.isw2.metrics.StructuralMetricsCalculator;
import it.uniroma2.isw2.model.CommitStats;
import it.uniroma2.isw2.model.DatasetRow;
import it.uniroma2.isw2.model.FileTouch;
import it.uniroma2.isw2.model.LabeledClassRelease;
import it.uniroma2.isw2.model.ProcessMetrics;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.ReleaseSelector;
import it.uniroma2.isw2.model.StructuralMetrics;
import it.uniroma2.isw2.smell.PmdSmellRunner;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Milestone1DatasetMain {

    private static final String REPOSITORY_PATH = "C:/Users/Valen/Desktop/syncope";
    private static final String PROJECT_NAME = "SYNCOPE";
    private static final String OUTPUT_CSV = "dataset_SYNCOPE_final.csv";

    public static void main(String[] args) throws IOException, InterruptedException {
        List<LabeledClassRelease> labeledRows =
                new DatasetCsvReader().readLabeledInventory("labeled_inventory_checkpoint.csv");
        System.out.println("Righe lette dal checkpoint: " + labeledRows.size());

        List<Release> allReleases = new ReleaseCsvReader().readReleases("SYNCOPEVersionInfo.csv");
        List<Release> selected = ReleaseSelector.selectFirstN(allReleases, 25);

        GitCommandExecutor git = new GitCommandExecutor(REPOSITORY_PATH);
        ReleaseSnapshotResolver snapshotResolver = new ReleaseSnapshotResolver(git);
        Map<Integer, String> snapshotCommits = snapshotResolver.resolveSnapshotCommits(selected);
        System.out.println("Snapshot risolti: " + snapshotCommits.size() + " su " + selected.size());

        Set<String> fixCommitHashes = new DatasetCsvReader().readFixCommitHashes("fix_commits_checkpoint.csv");
        System.out.println("Fix commit hash distinti: " + fixCommitHashes.size());

        // ---- setup rapido: commit stats per release + date finestre ----
        Map<Integer, Map<String, CommitStats>> commitStatsByRelease = new HashMap<>();
        ReleaseCommitStatsHarvester statsHarvester = new ReleaseCommitStatsHarvester(git);
        for (Release r : selected) {
            String commitHash = snapshotCommits.get(r.getId());
            if (commitHash == null) continue;
            commitStatsByRelease.put(r.getId(), statsHarvester.harvestForRelease(commitHash));
            System.out.println("Release " + r.getId() + ": commit stats pronte");
        }

        Map<Integer, LocalDateTime> releaseDateById = new HashMap<>();
        for (Release r : selected) releaseDateById.put(r.getId(), r.getReleaseDate());
        Map<Integer, LocalDateTime> windowStartByReleaseId = new HashMap<>();
        for (Release r : selected) windowStartByReleaseId.put(r.getId(), releaseDateById.get(r.getId() - 1));

        // ---- setup rapido: smell PMD per release ----
        Map<Integer, List<String>> classPathsByRelease = new HashMap<>();
        for (LabeledClassRelease row : labeledRows) {
            classPathsByRelease.computeIfAbsent(row.getReleaseId(), k -> new ArrayList<>()).add(row.getClassPath());
        }

        String pmdExecutablePath = "C:/Users/Valen/Downloads/pmd-dist-7.26.0-bin/pmd-bin-7.26.0/bin/pmd.bat";
        PmdSmellRunner pmdRunner = new PmdSmellRunner(git, pmdExecutablePath, Path.of("pmd-work"));

        Map<String, Integer> smellsByReleaseAndClass = new HashMap<>();
        for (Release r : selected) {
            String commitHash = snapshotCommits.get(r.getId());
            List<String> classPaths = classPathsByRelease.get(r.getId());
            if (commitHash == null || classPaths == null) continue;
            Map<String, Integer> smells = pmdRunner.countSmellsForRelease(r.getId(), commitHash, classPaths);
            for (Map.Entry<String, Integer> e : smells.entrySet()) {
                smellsByReleaseAndClass.put(r.getId() + "#" + e.getKey(), e.getValue());
            }
            System.out.println("Release " + r.getId() + ": PMD completato, " + smells.size() + " classi con smell");
        }

        // ---- loop finale unico: strutturali + processo + smell -> scrittura CSV ----
        StructuralMetricsCalculator structuralCalc = new StructuralMetricsCalculator(git);
        PerPathHistoryFetcher pathFetcher = new PerPathHistoryFetcher(git);
        ProcessMetricsCalculator processCalc = new ProcessMetricsCalculator(fixCommitHashes);

        int processed = 0;
        long buggyCount = 0;

        try (FileWriter writer = new FileWriter(OUTPUT_CSV)) {
            writer.write(DatasetRow.csvHeader());
            writer.write("\n");

            for (LabeledClassRelease row : labeledRows) {
                String commitHash = snapshotCommits.get(row.getReleaseId());
                if (commitHash == null) {
                    continue;
                }

                StructuralMetrics sm = structuralCalc.compute(commitHash, row.getClassPath());

                List<FileTouch> touches = pathFetcher.fetchHistoryUntilRelease(commitHash, row.getClassPath());
                Map<String, CommitStats> statsForRelease =
                        commitStatsByRelease.getOrDefault(row.getReleaseId(), Map.of());
                LocalDateTime windowEnd = releaseDateById.get(row.getReleaseId());
                LocalDateTime windowStart = windowStartByReleaseId.get(row.getReleaseId());
                ProcessMetrics pm = processCalc.compute(touches, statsForRelease, windowStart, windowEnd);

                int nSmells = smellsByReleaseAndClass.getOrDefault(
                        row.getReleaseId() + "#" + row.getClassPath(), 0);

                DatasetRow datasetRow = new DatasetRow(
                        PROJECT_NAME, row.getReleaseId(), row.getClassPath(),
                        sm.getNom(), sm.getFanOut(), sm.getCyclomaticComplexity(),
                        pm.getLocTouched(), pm.getNFix(), pm.getNAuth(), pm.getLocAdded(), pm.getMaxLocAdded(),
                        pm.getChurn(), pm.getMaxChurn(), pm.getNs(), pm.getNd(), pm.getChangeSetSize(), pm.getAgeDays(),
                        nSmells, row.isBuggy()
                );

                writer.write(datasetRow.toCsvRow());
                writer.write("\n");

                processed++;
                if (row.isBuggy()) {
                    buggyCount++;
                }

                if (processed % 500 == 0) {
                    writer.flush();
                    System.out.println("Scritte " + processed + " righe su " + labeledRows.size() + "...");
                }
            }
        }

        System.out.println("Dataset finale scritto in " + OUTPUT_CSV);
        System.out.println("Righe totali: " + processed);
        System.out.printf("Buggy: %d (%.2f%%)%n", buggyCount, 100.0 * buggyCount / processed);
    }
}