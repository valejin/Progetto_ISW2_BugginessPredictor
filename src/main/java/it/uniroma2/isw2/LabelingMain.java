package it.uniroma2.isw2;

import it.uniroma2.isw2.acquisition.ReleaseCsvReader;
import it.uniroma2.isw2.acquisition.TicketCsvReader;
import it.uniroma2.isw2.dataset.DatasetCsvWriter;
import it.uniroma2.isw2.linkage.*;
import it.uniroma2.isw2.model.*;
import it.uniroma2.isw2.proportion.AffectedVersionIvResolver;
import it.uniroma2.isw2.proportion.OpeningFixVersionCalculator;
import it.uniroma2.isw2.proportion.ProportionEstimator;
import it.uniroma2.isw2.util.AppLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LabelingMain {

    public static void main(String[] args) throws IOException, InterruptedException {
        List<Release> allReleases = new ReleaseCsvReader().readReleases("SYNCOPEVersionInfo.csv");
        List<Release> selected = ReleaseSelector.selectFirstN(allReleases, 25);
        List<Ticket> tickets = new TicketCsvReader().readTickets("SYNCOPETickets.csv");

        AppLogger.info("Release totali: " + allReleases.size());
        AppLogger.info("Release selezionate: " + selected.size());
        AppLogger.info("Ticket letti: " + tickets.size());


        // proportion
        OpeningFixVersionCalculator ovFvCalc = new OpeningFixVersionCalculator(allReleases);
        AffectedVersionIvResolver ivResolver = new AffectedVersionIvResolver(allReleases);
        ProportionEstimator proportionEstimator = new ProportionEstimator();

        for (Ticket t : tickets) {
            ovFvCalc.computeOpeningAndFixVersion(t);
        }

        // Scarta i ticket senza OV valida (aperti prima della primissima release nota):
        int beforeFilter = tickets.size();
        tickets.removeIf(t -> t.getOpeningVersion() == null);
        int discardedForNullOv = beforeFilter - tickets.size();
        AppLogger.info("Ticket scartati per OV nulla (creati prima della prima release): " + discardedForNullOv);

        List<Ticket> withKnownIv = new ArrayList<>();
        List<Ticket> toEstimate = new ArrayList<>();

        for (Ticket t : tickets) {
            if (t.getFixVersion() == null) {
                continue; // fix non ancora in nessuna release nota: escluso dal labeling
            }
            Optional<Integer> iv = ivResolver.resolveIv(t);
            if (iv.isPresent()) {
                t.setInjectedVersion(iv.get());
                withKnownIv.add(t);
            } else {
                toEstimate.add(t);
            }
        }

        double avgP = proportionEstimator.computeAverageProportion(withKnownIv);
        AppLogger.info("Proportion media: " + avgP);
        AppLogger.info("Ticket con IV nota da AV: " + withKnownIv.size());
        AppLogger.info("Ticket da stimare con Proportion: " + toEstimate.size());

        for (Ticket t : toEstimate) {
            t.setInjectedVersion(proportionEstimator.estimateIv(t, avgP));
        }


        // git + snapshot delle release (spostato qui: serve agli hash prima del linkage)
        String repositoryPath = "C:/Users/Valen/Desktop/syncope"; // path progetto locale

        GitCommandExecutor git = new GitCommandExecutor(repositoryPath);

        ReleaseSnapshotResolver snapshotResolver = new ReleaseSnapshotResolver(git);
        Map<Integer, String> snapshotCommits = snapshotResolver.resolveSnapshotCommits(selected);
        AppLogger.info("Snapshot risolti: " + snapshotCommits.size() + " su " + selected.size());


        // linkage — ora ancorato all'unione delle ancestry dei 25 tag, non solo master
        FixCommitLinker fixCommitLinker = new FixCommitLinker(git);
        BuggyClassExtractor buggyClassExtractor = new BuggyClassExtractor(git);

        List<FixCommit> fixCommits = fixCommitLinker.findFixCommits(tickets, new ArrayList<>(snapshotCommits.values()));
        AppLogger.info("Fix commit trovati: " + fixCommits.size());

        List<BuggyClassEntry> buggyClassEntries = buggyClassExtractor.extract(fixCommits);
        AppLogger.info("Coppie (ticket, classe buggy): " + buggyClassEntries.size());


        // labeling (riusa snapshotCommits già risolto sopra, nessuna doppia chiamata)
        ReleaseClassInventory inventoryBuilder = new ReleaseClassInventory(git);
        List<ReleaseClassEntry> inventory = inventoryBuilder.buildInventory(snapshotCommits);
        AppLogger.info("Righe inventario (classe, release): " + inventory.size());

        List<Ticket> allValidTickets = new ArrayList<>();
        allValidTickets.addAll(withKnownIv);
        allValidTickets.addAll(toEstimate);

        BugLabeler bugLabeler = new BugLabeler();
        Map<String, Boolean> labels = bugLabeler.labelClasses(inventory, allValidTickets, buggyClassEntries);

        long buggyCount = labels.values().stream().filter(Boolean::booleanValue).count();
        AppLogger.info("Coppie (classe, release) totali: " + labels.size());
        AppLogger.info(String.format("Etichettate buggy: %d (%.2f%%)", buggyCount, 100.0 * buggyCount / labels.size()));


        // checkpoint inventario
        DatasetCsvWriter csvWriter = new DatasetCsvWriter();
        csvWriter.writeLabeledInventory("labeled_inventory_checkpoint.csv", "SYNCOPE", inventory, labels);
        AppLogger.info("Checkpoint scritto: labeled_inventory_checkpoint.csv");

        // checkpoint
        csvWriter.writeFixCommits("fix_commits_checkpoint.csv", fixCommits);
        AppLogger.info("Checkpoint scritto: fix_commits_checkpoint.csv");
    }
}