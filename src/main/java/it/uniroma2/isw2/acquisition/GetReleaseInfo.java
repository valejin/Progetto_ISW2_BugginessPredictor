package it.uniroma2.isw2.acquisition;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Estrae dalla REST API di Jira Apache l'elenco delle release del progetto
 * e le scrive in un CSV ordinate per data, con un ID incrementale intero.
 */
public class GetReleaseInfo {

    private static final Logger LOGGER = Logger.getLogger(GetReleaseInfo.class.getName());
    private static final String PROJECT_NAME = "SYNCOPE";
    private static final String JIRA_PROJECT_URL =
            "https://issues.apache.org/jira/rest/api/2/project/" + PROJECT_NAME;

    public static void main(String[] args) throws IOException, JSONException {
        List<LocalDateTime> releaseDates = new ArrayList<>();
        Map<LocalDateTime, String> releaseNames = new HashMap<>();
        Map<LocalDateTime, String> releaseIds = new HashMap<>();

        JSONObject json = readJsonFromUrl(JIRA_PROJECT_URL);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (!version.has("releaseDate")) {
                continue; // ignora le release senza data, come indicato dal prof
            }
            String name = version.optString("name", "");
            String id = version.optString("id", "");
            LocalDate date = LocalDate.parse(version.getString("releaseDate"));
            LocalDateTime dateTime = date.atStartOfDay();

            if (!releaseDates.contains(dateTime)) {
                releaseDates.add(dateTime);
            }
            releaseNames.put(dateTime, name);
            releaseIds.put(dateTime, id);
        }

        releaseDates.sort(Comparator.naturalOrder());

        if (releaseDates.size() < 6) {
            LOGGER.warning("Meno di 6 release trovate: controlla il nome del progetto o la risposta API.");
            return;
        }

        String outputFile = PROJECT_NAME + "VersionInfo.csv";
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.append("Index,Version ID,Version Name,Date\n");
            for (int i = 0; i < releaseDates.size(); i++) {
                LocalDateTime date = releaseDates.get(i);
                writer.append(String.valueOf(i + 1)).append(",")
                        .append(releaseIds.get(date)).append(",")
                        .append(releaseNames.get(date)).append(",")
                        .append(date.toString()).append("\n");
            }
        }

        LOGGER.log(Level.INFO, "Release scritte in {0}: {1}", new Object[]{outputFile, releaseDates.size()});
    }

    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = rd.read()) != -1) {
                sb.append((char) c);
            }
            return new JSONObject(sb.toString());
        }
    }
}