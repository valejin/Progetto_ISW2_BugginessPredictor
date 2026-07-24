package it.uniroma2.isw2.weka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unisce Precision/Recall/F-Measure/AUC/Kappa (100 run, da
 * weka_results_summary.csv) con NPofB20 (10 ripetizioni, media calcolata
 * da npofb20_distribution.csv generato da NPofB20DistributionCalculator),
 * producendo la tabella finale della Milestone 2.
 */
public class MetricsCalculator {

    private static final String WEKA_DATA_DIR = "weka-data";
    private static final String SUMMARY_PATH = WEKA_DATA_DIR + "/weka_results_summary.csv";
    private static final String NPOFB20_DISTRIBUTION_PATH = WEKA_DATA_DIR + "/npofb20_distribution.csv";
    private static final String OUTPUT_PATH = WEKA_DATA_DIR + "/final_results_table.csv";

    public static void main(String[] args) throws IOException {
        List<Map<String, String>> summaryRows = readCsv(SUMMARY_PATH);
        System.out.println("Righe lette da weka_results_summary.csv: " + summaryRows.size());

        Map<String, List<Double>> npofb20ByKey = new TreeMap<>();
        List<Map<String, String>> distributionRows = readCsv(NPOFB20_DISTRIBUTION_PATH);
        System.out.println("Righe lette da npofb20_distribution.csv: " + distributionRows.size());

        for (Map<String, String> row : distributionRows) {
            String key = row.get("Configurazione") + "|" + row.get("Classificatore");
            npofb20ByKey.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(Double.parseDouble(row.get("NPofB20")));
        }

        writeFinalTable(OUTPUT_PATH, summaryRows, npofb20ByKey);
    }

    private static void writeFinalTable(String outputPath, List<Map<String, String>> summaryRows,
                                        Map<String, List<Double>> npofb20ByKey) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("Configurazione,Classificatore,Precision,Recall,F_Measure,AUC,Kappa,NPofB20\n");

            for (Map<String, String> row : summaryRows) {
                String config = row.get("Configurazione");
                String classifier = row.get("Classificatore");
                String key = config + "|" + classifier;
                List<Double> values = npofb20ByKey.get(key);

                if (values == null || values.isEmpty()) {
                    System.out.println("ATTENZIONE: nessun valore NPofB20 trovato per " + key);
                    continue;
                }

                double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                writer.write(String.format(Locale.US, "%s,%s,%s,%s,%s,%s,%s,%.2f%n",
                        config, classifier,
                        row.get("Precision_Yes_media"), row.get("Recall_Yes_media"), row.get("F1_Yes_media"),
                        row.get("AUC_media"), row.get("Kappa_media"), mean));
            }
        }
        System.out.println("Tabella finale scritta in " + outputPath);
    }

    private static List<Map<String, String>> readCsv(String path) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String headerLine = reader.readLine();
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split(",", -1);
                Map<String, String> row = new java.util.HashMap<>();
                for (int i = 0; i < headers.length; i++) row.put(headers[i], fields[i]);
                result.add(row);
            }
        }
        return result;
    }
}