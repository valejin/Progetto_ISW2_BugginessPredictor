package it.uniroma2.isw2.dataset;

import it.uniroma2.isw2.model.FixCommit;
import it.uniroma2.isw2.model.ReleaseClassEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DatasetCsvWriter {

    public void writeLabeledInventory(String outputPath,
                                      String projectName,
                                      List<ReleaseClassEntry> inventory,
                                      Map<String, Boolean> labels) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.append("Project,Release,ClassName,Bugginess\n");
            for (ReleaseClassEntry rc : inventory) {
                String key = rc.getReleaseId() + "#" + rc.getClassPath();
                boolean buggy = labels.getOrDefault(key, false);
                writer.append(projectName).append(",")
                        .append(String.valueOf(rc.getReleaseId())).append(",")
                        .append("\"").append(rc.getClassPath()).append("\",")
                        .append(buggy ? "Yes" : "No").append("\n");
            }
        }
    }


    public void writeFixCommits(String outputPath, List<FixCommit> fixCommits) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.append("TicketID,CommitHash\n");
            for (FixCommit fc : fixCommits) {
                writer.append(fc.getTicketId()).append(",").append(fc.getCommitHash()).append("\n");
            }
        }
    }
}