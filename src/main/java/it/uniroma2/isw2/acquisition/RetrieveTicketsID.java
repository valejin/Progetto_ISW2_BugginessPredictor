package it.uniroma2.isw2.acquisition;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Estrae dalla REST API di Jira Apache i ticket di tipo Bug, chiusi o
 * risolti con resolution Fixed, e li scrive in un CSV con data di
 * creazione, data di risoluzione e affected version (separate da ";").
 */
class RetrieveTicketsID {

    private static final Logger LOGGER = Logger.getLogger(RetrieveTicketsID.class.getName());
    private static final String PROJECT_NAME = "SYNCOPE";
    private static final int PAGE_SIZE = 1000;

    public static void main(String[] args) throws IOException, JSONException {
        String outputFile = PROJECT_NAME + "Tickets.csv";

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.append("TicketID,CreationDate,ResolutionDate,AffectedVersions\n");

            int startAt = 0;
            int total = 1;
            int written = 0;

            while (startAt < total) {
                String jql = "project = \"" + PROJECT_NAME + "\" AND issueType = \"Bug\" "
                        + "AND (status = \"closed\" OR status = \"resolved\") "
                        + "AND resolution = \"fixed\"";

                String url = "https://issues.apache.org/jira/rest/api/2/search"
                        + "?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                        + "&fields=key,created,resolutiondate,versions"
                        + "&startAt=" + startAt
                        + "&maxResults=" + PAGE_SIZE;

                JSONObject json = readJsonFromUrl(url);
                JSONArray issues = json.getJSONArray("issues");
                total = json.getInt("total");

                if (issues.isEmpty()) {
                    break; // sicurezza anti-loop infinito
                }

                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    JSONObject fields = issue.getJSONObject("fields");

                    String key = issue.getString("key");
                    String created = fields.optString("created", "");
                    String resolutionDate = fields.optString("resolutiondate", "");
                    String affectedVersions = extractAffectedVersions(fields);

                    writer.append("\"").append(key).append("\",")
                            .append("\"").append(created).append("\",")
                            .append("\"").append(resolutionDate).append("\",")
                            .append("\"").append(affectedVersions).append("\"\n");
                    written++;
                }

                startAt += issues.length();
            }

            LOGGER.log(Level.INFO, "Ticket scritti in {0}: {1}", new Object[]{outputFile, written});
        }
    }

    private static String extractAffectedVersions(JSONObject fields) {
        if (!fields.has("versions")) {
            return "";
        }
        JSONArray versions = fields.getJSONArray("versions");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < versions.length(); i++) {
            if (i > 0) {
                sb.append(";");
            }
            sb.append(versions.getJSONObject(i).optString("name", ""));
        }
        return sb.toString();
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