package it.uniroma2.isw2.acquisition;

import it.uniroma2.isw2.model.Ticket;

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
}