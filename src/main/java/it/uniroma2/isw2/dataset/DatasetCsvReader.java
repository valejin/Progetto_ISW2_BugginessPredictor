package it.uniroma2.isw2.dataset;

import it.uniroma2.isw2.model.LabeledClassRelease;

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

    // Split che rispetta le virgolette, cosi' una virgola dentro un campo
    // quotato non spezza la riga a meta'. Parsing a scansione lineare (niente regex):
    // la versione precedente basata su regex con lookahead e quantificatori annidati
    // poteva causare backtracking catastrofico/StackOverflowError su righe lunghe.
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
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