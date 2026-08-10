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
import it.uniroma2.isw2.util.AppLogger;
import it.uniroma2.isw2.util.ProjectConfig;

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

    private static final String PROJECT_NAME = "SYNCOPE";
    private static final String OUTPUT_CSV = "dataset_SYNCOPE_final.csv";

    public static void main(String[] args) throws IOException, InterruptedException {
        List<LabeledClassRelease> labeledRows =
                new DatasetCsvReader().readLabeledInventory("labeled_inventory_checkpoint.csv");
        AppLogger.info("Righe lette dal checkpoint: " + labeledRows.size());

        List<Release> allReleases = new ReleaseCsvReader().readReleases("SYNCOPEVersionInfo.csv");
        List<Release> selected = ReleaseSelector.selectFirstN(allReleases, 25);

        GitCommandExecutor git = new GitCommandExecutor(ProjectConfig.repositoryPath());
        ReleaseSnapshotResolver snapshotResolver = new ReleaseSnapshotResolver(git);
        Map<Integer, String> snapshotCommits = snapshotResolver.resolveSnapshotCommits(selected);
        AppLogger.info("Snapshot risolti: " + snapshotCommits.size() + " su " + selected.size());

        Set<String> fixCommitHashes = new DatasetCsvReader().readFixCommitHashes("fix_commits_checkpoint.csv");
        AppLogger.info("Fix commit hash distinti: " + fixCommitHashes.size());

        Map<Integer, Map<String, CommitStats>> commitStatsByRelease =
                buildCommitStatsByRelease(git, selected, snapshotCommits);
        Map<Integer, LocalDateTime> releaseDateById = buildReleaseDateById(selected);
        Map<Integer, LocalDateTime> windowStartByReleaseId = buildWindowStartByReleaseId(selected, releaseDateById);
        Map<Integer, List<String>> classPathsByRelease = buildClassPathsByRelease(labeledRows);

        PmdSmellRunner pmdRunner = new PmdSmellRunner(git, ProjectConfig.pmdExecutablePath(), Path.of("pmd-work"));
        Map<String, Integer> smellsByReleaseAndClass =
                computeSmellsByReleaseAndClass(pmdRunner, selected, snapshotCommits, classPathsByRelease);

        StructuralMetricsCalculator structuralCalc = new StructuralMetricsCalculator(git);
        PerPathHistoryFetcher pathFetcher = new PerPathHistoryFetcher(git);
        ProcessMetricsCalculator processCalc = new ProcessMetricsCalculator(fixCommitHashes);

        DatasetWriteResult result = writeDataset(labeledRows, snapshotCommits, structuralCalc, pathFetcher,
                processCalc, commitStatsByRelease, releaseDateById, windowStartByReleaseId, smellsByReleaseAndClass);

        AppLogger.info("Dataset finale scritto in " + OUTPUT_CSV);
        AppLogger.info("Righe totali: " + result.processed());
        AppLogger.info(String.format("Buggy: %d (%.2f%%)", result.buggyCount(),
                100.0 * result.buggyCount() / result.processed()));
    }

    /** Commit stats per release, indicizzate per releaseId (solo release con snapshot risolto). */
    private static Map<Integer, Map<String, CommitStats>> buildCommitStatsByRelease(
            GitCommandExecutor git, List<Release> selected, Map<Integer, String> snapshotCommits)
            throws IOException, InterruptedException {
        Map<Integer, Map<String, CommitStats>> commitStatsByRelease = new HashMap<>();
        ReleaseCommitStatsHarvester statsHarvester = new ReleaseCommitStatsHarvester(git);
        for (Release r : selected) {
            String commitHash = snapshotCommits.get(r.getId());
            if (commitHash == null) {
                continue;
            }
            commitStatsByRelease.put(r.getId(), statsHarvester.harvestForRelease(commitHash));
            AppLogger.info("Release " + r.getId() + ": commit stats pronte");
        }
        return commitStatsByRelease;
    }

    /** Data di ogni release, indicizzata per releaseId. */
    private static Map<Integer, LocalDateTime> buildReleaseDateById(List<Release> selected) {
        Map<Integer, LocalDateTime> releaseDateById = new HashMap<>();
        for (Release r : selected) {
            releaseDateById.put(r.getId(), r.getReleaseDate());
        }
        return releaseDateById;
    }

    /** Inizio finestra di osservazione di ogni release: la data della release precedente. */
    private static Map<Integer, LocalDateTime> buildWindowStartByReleaseId(
            List<Release> selected, Map<Integer, LocalDateTime> releaseDateById) {
        Map<Integer, LocalDateTime> windowStartByReleaseId = new HashMap<>();
        for (Release r : selected) {
            windowStartByReleaseId.put(r.getId(), releaseDateById.get(r.getId() - 1));
        }
        return windowStartByReleaseId;
    }

    /** Class path presenti in ciascuna release, a partire dall'inventario etichettato. */
    private static Map<Integer, List<String>> buildClassPathsByRelease(List<LabeledClassRelease> labeledRows) {
        Map<Integer, List<String>> classPathsByRelease = new HashMap<>();
        for (LabeledClassRelease row : labeledRows) {
            classPathsByRelease.computeIfAbsent(row.getReleaseId(), k -> new ArrayList<>()).add(row.getClassPath());
        }
        return classPathsByRelease;
    }

    /** Numero di smell PMD per coppia (releaseId, classPath), con chiave "releaseId#classPath". */
    private static Map<String, Integer> computeSmellsByReleaseAndClass(
            PmdSmellRunner pmdRunner, List<Release> selected, Map<Integer, String> snapshotCommits,
            Map<Integer, List<String>> classPathsByRelease) throws IOException, InterruptedException {
        Map<String, Integer> smellsByReleaseAndClass = new HashMap<>();
        for (Release r : selected) {
            String commitHash = snapshotCommits.get(r.getId());
            List<String> classPaths = classPathsByRelease.get(r.getId());
            if (commitHash == null || classPaths == null) {
                continue;
            }
            Map<String, Integer> smells = pmdRunner.countSmellsForRelease(r.getId(), commitHash, classPaths);
            for (Map.Entry<String, Integer> e : smells.entrySet()) {
                smellsByReleaseAndClass.put(r.getId() + "#" + e.getKey(), e.getValue());
            }
            AppLogger.info("Release " + r.getId() + ": PMD completato, " + smells.size() + " classi con smell");
        }
        return smellsByReleaseAndClass;
    }

    /** Esito della scrittura del dataset: righe scritte e conteggio delle righe buggy. */
    private record DatasetWriteResult(int processed, long buggyCount) {
    }

    /** Loop finale: per ogni riga etichettata calcola metriche strutturali + di processo + smell e scrive il CSV. */
    private static DatasetWriteResult writeDataset(
            List<LabeledClassRelease> labeledRows, Map<Integer, String> snapshotCommits,
            StructuralMetricsCalculator structuralCalc, PerPathHistoryFetcher pathFetcher,
            ProcessMetricsCalculator processCalc, Map<Integer, Map<String, CommitStats>> commitStatsByRelease,
            Map<Integer, LocalDateTime> releaseDateById, Map<Integer, LocalDateTime> windowStartByReleaseId,
            Map<String, Integer> smellsByReleaseAndClass) throws IOException, InterruptedException {

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

                DatasetRow datasetRow = buildDatasetRow(row, commitHash, structuralCalc, pathFetcher, processCalc,
                        commitStatsByRelease, releaseDateById, windowStartByReleaseId, smellsByReleaseAndClass);

                writer.write(datasetRow.toCsvRow());
                writer.write("\n");

                processed++;
                if (row.isBuggy()) {
                    buggyCount++;
                }

                if (processed % 500 == 0) {
                    writer.flush();
                    AppLogger.info("Scritte " + processed + " righe su " + labeledRows.size() + "...");
                }
            }
        }

        return new DatasetWriteResult(processed, buggyCount);
    }

    /** Costruisce la riga di dataset per una singola coppia (classe, release). */
    private static DatasetRow buildDatasetRow(
            LabeledClassRelease row, String commitHash, StructuralMetricsCalculator structuralCalc,
            PerPathHistoryFetcher pathFetcher, ProcessMetricsCalculator processCalc,
            Map<Integer, Map<String, CommitStats>> commitStatsByRelease, Map<Integer, LocalDateTime> releaseDateById,
            Map<Integer, LocalDateTime> windowStartByReleaseId, Map<String, Integer> smellsByReleaseAndClass)
            throws IOException, InterruptedException {

        StructuralMetrics sm = structuralCalc.compute(commitHash, row.getClassPath());

        List<FileTouch> touches = pathFetcher.fetchHistoryUntilRelease(commitHash, row.getClassPath());
        Map<String, CommitStats> statsForRelease =
                commitStatsByRelease.getOrDefault(row.getReleaseId(), Map.of());
        LocalDateTime windowEnd = releaseDateById.get(row.getReleaseId());
        LocalDateTime windowStart = windowStartByReleaseId.get(row.getReleaseId());
        ProcessMetrics pm = processCalc.compute(touches, statsForRelease, windowStart, windowEnd);

        int nSmells = smellsByReleaseAndClass.getOrDefault(
                row.getReleaseId() + "#" + row.getClassPath(), 0);

        return new DatasetRow(
                PROJECT_NAME, row.getReleaseId(), row.getClassPath(),
                sm.getNom(), sm.getFanOut(), sm.getCyclomaticComplexity(),
                pm.getLocTouched(), pm.getNFix(), pm.getNAuth(), pm.getLocAdded(), pm.getMaxLocAdded(),
                pm.getChurn(), pm.getMaxChurn(), pm.getNs(), pm.getNd(), pm.getChangeSetSize(), pm.getAgeDays(),
                nSmells, row.isBuggy()
        );
    }
}