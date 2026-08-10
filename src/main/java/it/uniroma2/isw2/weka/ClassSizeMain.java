package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.dataset.DatasetCsvReader;
import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.model.LabeledClassRelease;
import it.uniroma2.isw2.util.AppLogger;
import it.uniroma2.isw2.util.ProjectConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Calcola la dimensione (LOC totali) di ogni classe in ogni release, dato
 * lo snapshot commit già risolto nel checkpoint. Necessario per calcolare
 * NPofB20 (effort-aware metric): l'effort si misura in LOC ispezionate,
 * non in numero di classi.
 */
public class ClassSizeMain {

    public static void main(String[] args) throws IOException, InterruptedException {
        List<LabeledClassRelease> labeledRows =
                new DatasetCsvReader().readLabeledInventory("labeled_inventory_checkpoint.csv");
        AppLogger.info("Righe lette: " + labeledRows.size());

        // Serve la mappa release -> commit hash: la ricalcoliamo qui,
        // stessa identica logica di ReleaseSnapshotResolver, per non
        // duplicare troppo import/dipendenze in questo tool leggero.
        var allReleases = new it.uniroma2.isw2.acquisition.ReleaseCsvReader().readReleases("SYNCOPEVersionInfo.csv");
        var selected = it.uniroma2.isw2.model.ReleaseSelector.selectFirstN(allReleases, 25);

        GitCommandExecutor git = new GitCommandExecutor(ProjectConfig.repositoryPath());
        var snapshotResolver = new it.uniroma2.isw2.linkage.ReleaseSnapshotResolver(git);
        var snapshotCommits = snapshotResolver.resolveSnapshotCommits(selected);
        AppLogger.info("Snapshot risolti: " + snapshotCommits.size());

        int processed = 0;
        try (FileWriter writer = new FileWriter("weka-data/class_size.csv")) {
            writer.write("Release,ClassName,SizeLOC\n");

            for (LabeledClassRelease row : labeledRows) {
                String commitHash = snapshotCommits.get(row.getReleaseId());
                if (commitHash == null) continue;

                List<String> lines = git.execute("git", "show", commitHash + ":" + row.getClassPath());
                int sizeLoc = lines.size();

                writer.write(row.getReleaseId() + ",\"" + row.getClassPath() + "\"," + sizeLoc + "\n");

                processed++;
                if (processed % 2000 == 0) {
                    writer.flush();
                    AppLogger.info("Processate " + processed + "/" + labeledRows.size());
                }
            }
        }
        AppLogger.info("Scritte " + processed + " righe in weka-data/class_size.csv");
    }
}