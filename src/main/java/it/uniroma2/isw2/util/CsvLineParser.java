package it.uniroma2.isw2.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Split di una riga CSV che rispetta le virgolette, cosi' una virgola dentro un campo
 * quotato non spezza la riga a meta'. Parsing a scansione lineare (niente regex): un
 * approccio basato su regex con lookahead e quantificatori annidati puo' causare
 * backtracking catastrofico/StackOverflowError su righe lunghe (rule java:S5998).
 * <p>
 * Centralizzata qui perche' era duplicata identica in piu' classi (TicketCsvReader,
 * DatasetCsvReader, PmdSmellRunner), generando duplicazione rilevata da SonarCloud.
 */
public final class CsvLineParser {

    private CsvLineParser() {
        // Classe di utilita': solo metodi statici, costruttore privato
    }

    public static String[] splitCsvLine(String line) {
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