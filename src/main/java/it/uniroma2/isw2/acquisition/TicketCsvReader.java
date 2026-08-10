package it.uniroma2.isw2.acquisition;

import it.uniroma2.isw2.model.Ticket;
import it.uniroma2.isw2.util.CsvLineParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TicketCsvReader {

    private static final DateTimeFormatter JIRA_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public List<Ticket> readTickets(String csvPath) throws IOException {
        List<Ticket> tickets = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // salta header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = splitCsvLine(line);

                String id = fields[0];
                LocalDateTime creationDate = parseJiraDate(fields[1]);
                LocalDateTime resolutionDate = parseJiraDate(fields[2]);
                List<String> affectedVersions = fields[3].isEmpty()
                        ? List.of()
                        : Arrays.asList(fields[3].split(";"));

                tickets.add(new Ticket(id, creationDate, resolutionDate, affectedVersions));
            }
        }
        return tickets;
    }

    private LocalDateTime parseJiraDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return OffsetDateTime.parse(raw, JIRA_DATE_FORMAT).toLocalDateTime();
    }

    // Split che rispetta le virgolette: implementazione centralizzata in CsvLineParser
    private String[] splitCsvLine(String line) {
        return CsvLineParser.splitCsvLine(line);
    }
}