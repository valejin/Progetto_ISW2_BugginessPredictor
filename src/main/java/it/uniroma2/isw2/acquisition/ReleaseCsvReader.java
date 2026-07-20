package it.uniroma2.isw2.acquisition;

import it.uniroma2.isw2.model.Release;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReleaseCsvReader {

    public List<Release> readReleases(String csvPath) throws IOException {
        List<Release> releases = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // salta header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", 4);
                int index = Integer.parseInt(fields[0].trim());
                String jiraId = fields[1].trim();
                String name = fields[2].trim();
                LocalDateTime date = LocalDateTime.parse(fields[3].trim());
                releases.add(new Release(index, jiraId, name, date));
            }
        }
        return releases;
    }
}