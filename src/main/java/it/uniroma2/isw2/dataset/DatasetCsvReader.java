package it.uniroma2.isw2.dataset;

import it.uniroma2.isw2.model.LabeledClassRelease;
import it.uniroma2.isw2.util.CsvLineParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatasetCsvReader {

    public List<LabeledClassRelease> readLabeledInventory(String csvPath) throws IOException {
        List<LabeledClassRelease> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // salta header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = splitCsvLine(line);
                int releaseId = Integer.parseInt(fields[1].trim());
                String classPath = fields[2];
                boolean buggy = "Yes".equalsIgnoreCase(fields[3].trim());
                result.add(new LabeledClassRelease(releaseId, classPath, buggy));
            }
        }
        return result;
    }

    // Split che rispetta le virgolette: implementazione centralizzata in CsvLineParser
    private String[] splitCsvLine(String line) {
        return CsvLineParser.splitCsvLine(line);
    }


    public Set<String> readFixCommitHashes(String csvPath) throws IOException {
        Set<String> hashes = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // salta header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split(",", 2);
                if (fields.length == 2) {
                    hashes.add(fields[1].trim());
                }
            }
        }
        return hashes;
    }
}