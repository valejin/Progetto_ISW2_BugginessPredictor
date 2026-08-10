package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.util.AppLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calcola le metriche medie su 100 run per ciascuna configurazione, a
 * partire dal CSV grezzo esportato da Weka Experimenter.
 *
 * IMPORTANTE: Precision, Recall e F-Measure sono calcolate rispetto alla
 * classe "Yes" (buggy), non come media pesata tra le due classi.
 */
public class WekaResultsAggregator {

    // Path relativi alla root del progetto (working directory di default in IntelliJ)
    private static final String INPUT_PATH = "weka-data/weka_results.csv";
    private static final String OUTPUT_PATH = "weka-data/weka_results_summary.csv";

    public static void main(String[] args) throws IOException {
        List<Map<String, String>> rows = readCsv(INPUT_PATH);
        AppLogger.info("Righe lette da " + INPUT_PATH + ": " + rows.size());

        Map<String, List<Map<String, String>>> groups = new TreeMap<>();
        for (Map<String, String> row : rows) {
            String config = identifyConfiguration(row.get("Key_Scheme"), row.get("Key_Scheme_options"));
            String classifier = identifyClassifier(row.get("Key_Scheme"), row.get("Key_Scheme_options"));
            String key = config + " | " + classifier;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        writeSummary(OUTPUT_PATH, groups);
    }

    private static String identifyClassifier(String scheme, String schemeOptions) {
        String combined = scheme + " " + schemeOptions;
        if (combined.contains("RandomForest")) return "RandomForest";
        if (combined.contains("NaiveBayes")) return "NaiveBayes";
        if (combined.contains(".IBk")) return "IBk";
        return "SCONOSCIUTO";
    }

    private static String identifyConfiguration(String scheme, String schemeOptions) {
        if (scheme.contains("AttributeSelectedClassifier")) return "Solo FS";
        if (schemeOptions.contains("MultiFilter")) return "FS + Balancing";
        if (schemeOptions.contains("Resample")) return "Solo Balancing";
        if (scheme.equals("weka.classifiers.trees.RandomForest")
                || scheme.equals("weka.classifiers.bayes.NaiveBayes")
                || scheme.equals("weka.classifiers.lazy.IBk")) {
            return "Baseline";
        }
        return "SCONOSCIUTA";
    }

    private static void writeSummary(String outputPath, Map<String, List<Map<String, String>>> groups)
            throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("Configurazione,Classificatore,NumeroRun,"
                    + "Precision_Yes_media,Precision_Yes_devstd,"
                    + "Recall_Yes_media,Recall_Yes_devstd,"
                    + "F1_Yes_media,F1_Yes_devstd,"
                    + "AUC_media,AUC_devstd,"
                    + "Kappa_media,Kappa_devstd\n");

            for (Map.Entry<String, List<Map<String, String>>> entry : groups.entrySet()) {
                writer.write(buildGroupLine(entry.getKey(), entry.getValue()) + "\n");
            }
        }
        AppLogger.info("Scritte " + groups.size() + " righe di riepilogo in " + outputPath);
    }

    /** Riga CSV di riepilogo (media + devstd delle 5 metriche) per una singola coppia configurazione/classificatore. */
    private static String buildGroupLine(String groupKey, List<Map<String, String>> groupRows) {
        String[] parts = groupKey.split(" \\| ");
        String config = parts[0];
        String classifier = parts[1];

        if (groupRows.size() != 100) {
            AppLogger.warn(groupKey + " ha " + groupRows.size() + " run, atteso 100");
        }

        double[][] metrics = extractMetrics(groupRows);

        StringBuilder line = new StringBuilder(config + "," + classifier + "," + groupRows.size());
        for (double[] arr : metrics) {
            double[] stats = meanAndStdDev(arr);
            line.append(",").append(String.format(Locale.US, "%.4f", stats[0]));
            line.append(",").append(String.format(Locale.US, "%.4f", stats[1]));
        }
        return line.toString();
    }

    /** Estrae, per ogni run del gruppo, i 5 array di metriche (Precision/Recall/F1 sulla classe Yes, AUC, Kappa). */
    private static double[][] extractMetrics(List<Map<String, String>> groupRows) {
        int n = groupRows.size();
        double[] precisions = new double[n];
        double[] recalls = new double[n];
        double[] f1s = new double[n];
        double[] aucs = new double[n];
        double[] kappas = new double[n];

        for (int i = 0; i < n; i++) {
            Map<String, String> row = groupRows.get(i);
            double tnNo = Double.parseDouble(row.get("Num_true_negatives"));
            double fnNo = Double.parseDouble(row.get("Num_false_negatives"));
            double fpNo = Double.parseDouble(row.get("Num_false_positives"));

            double precisionYes = (tnNo + fnNo) > 0 ? tnNo / (tnNo + fnNo) : 0;
            double recallYes = (tnNo + fpNo) > 0 ? tnNo / (tnNo + fpNo) : 0;
            double f1Yes = (precisionYes + recallYes) > 0
                    ? 2 * precisionYes * recallYes / (precisionYes + recallYes) : 0;

            precisions[i] = precisionYes;
            recalls[i] = recallYes;
            f1s[i] = f1Yes;
            aucs[i] = Double.parseDouble(row.get("Weighted_avg_area_under_ROC"));
            kappas[i] = Double.parseDouble(row.get("Kappa_statistic"));
        }

        return new double[][]{precisions, recalls, f1s, aucs, kappas};
    }

    private static double[] meanAndStdDev(double[] values) {
        int n = values.length;
        double sum = 0;
        for (double v : values) sum += v;
        double mean = sum / n;

        double sumSquaredDiff = 0;
        for (double v : values) sumSquaredDiff += (v - mean) * (v - mean);
        double stdDev = Math.sqrt(sumSquaredDiff / n);

        return new double[]{mean, stdDev};
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

                if (fields.length != headers.length) {
                    throw new IllegalStateException(
                            "Riga con numero di campi inatteso (" + fields.length + " invece di "
                                    + headers.length + "). Riga: " + line);
                }

                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], fields[i]);
                }
                result.add(row);
            }
        }
        return result;
    }
}