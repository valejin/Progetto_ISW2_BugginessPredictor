package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.util.AppLogger;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.awt.BasicStroke;


/**
 * Genera un boxplot per ciascuna delle 5 metriche della Milestone 2
 * (Precision, Recall, Kappa, AUC, NPofB20), tutti con lo stesso asse
 * Y fisso 0.0-1.0 per essere direttamente confrontabili. NPofB20 viene
 * convertita da percentuale a frazione per uniformita' di scala.
 */
public class MetricsBoxplotGenerator {

    private static final String WEKA_RESULTS_PATH = "weka-data/weka_results.csv";
    private static final String NPOFB20_PATH = "weka-data/npofb20_distribution.csv";
    private static final String OUTPUT_DIR = "weka-data/boxplots";

    private static final String CLASSIFIER_NAIVE_BAYES = "NaiveBayes";
    private static final String CLASSIFIER_RANDOM_FOREST = "RandomForest";
    private static final String CLASSIFIER_IBK = "IBk";
    private static final String CLASSIFIER_UNKNOWN = "SCONOSCIUTO";

    private static final String[] CONFIGS = {"Baseline", "Solo FS", "Solo Balancing", "FS + Balancing"};
    private static final String[] CLASSIFIERS = {CLASSIFIER_NAIVE_BAYES, CLASSIFIER_RANDOM_FOREST, CLASSIFIER_IBK};
    private static final Color[] SERIES_COLORS = {
            new Color(76, 114, 176),   // NaiveBayes - blu
            new Color(221, 132, 82),   // RandomForest - arancione
            new Color(85, 168, 104)    // IBk - verde
    };

    public static void main(String[] args) throws IOException {
        new File(OUTPUT_DIR).mkdirs();

        List<Map<String, String>> rawResults = readCsv(WEKA_RESULTS_PATH);
        AppLogger.info("Righe lette da weka_results.csv: " + rawResults.size());

        Map<String, Map<String, List<Double>>> precisionData = new LinkedHashMap<>();
        Map<String, Map<String, List<Double>>> recallData = new LinkedHashMap<>();
        Map<String, Map<String, List<Double>>> kappaData = new LinkedHashMap<>();
        Map<String, Map<String, List<Double>>> aucData = new LinkedHashMap<>();

        for (Map<String, String> row : rawResults) {
            String config = identifyConfiguration(row.get("Key_Scheme"), row.get("Key_Scheme_options"));
            String classifier = identifyClassifier(row.get("Key_Scheme"), row.get("Key_Scheme_options"));
            if (config.equals("SCONOSCIUTA") || classifier.equals(CLASSIFIER_UNKNOWN)) continue;

            double tnNo = Double.parseDouble(row.get("Num_true_negatives"));
            double fnNo = Double.parseDouble(row.get("Num_false_negatives"));
            double fpNo = Double.parseDouble(row.get("Num_false_positives"));

            double precisionYes = (tnNo + fnNo) > 0 ? tnNo / (tnNo + fnNo) : 0;
            double recallYes = (tnNo + fpNo) > 0 ? tnNo / (tnNo + fpNo) : 0;
            double kappa = Double.parseDouble(row.get("Kappa_statistic"));
            double auc = Double.parseDouble(row.get("Weighted_avg_area_under_ROC"));

            addValue(precisionData, config, classifier, precisionYes);
            addValue(recallData, config, classifier, recallYes);
            addValue(kappaData, config, classifier, kappa);
            addValue(aucData, config, classifier, auc);
        }

        Map<String, Map<String, List<Double>>> npofb20Data = new LinkedHashMap<>();
        List<Map<String, String>> npofb20Rows = readCsv(NPOFB20_PATH);
        AppLogger.info("Righe lette da npofb20_distribution.csv: " + npofb20Rows.size());
        for (Map<String, String> row : npofb20Rows) {
            String config = row.get("Configurazione");
            String classifier = row.get("Classificatore");
            double npofb20Fraction = Double.parseDouble(row.get("NPofB20")) / 100.0; // percentuale -> frazione
            addValue(npofb20Data, config, classifier, npofb20Fraction);
        }

        generateBoxplot("Precision (classe Yes)", precisionData, OUTPUT_DIR + "/boxplot_precision.png");
        generateBoxplot("Recall (classe Yes)", recallData, OUTPUT_DIR + "/boxplot_recall.png");
        generateBoxplot("Kappa", kappaData, OUTPUT_DIR + "/boxplot_kappa.png");
        generateBoxplot("AUC", aucData, OUTPUT_DIR + "/boxplot_auc.png");
        generateBoxplot("NPofB20", npofb20Data, OUTPUT_DIR + "/boxplot_npofb20.png");

        AppLogger.info("Boxplot generati in " + OUTPUT_DIR);
    }

    private static void addValue(Map<String, Map<String, List<Double>>> data,
                                 String config, String classifier, double value) {
        data.computeIfAbsent(config, k -> new LinkedHashMap<>())
                .computeIfAbsent(classifier, k -> new ArrayList<>())
                .add(value);
    }

    private static void generateBoxplot(String metricName, Map<String, Map<String, List<Double>>> data,
                                        String outputPath) throws IOException {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();

        for (String config : CONFIGS) {
            Map<String, List<Double>> byClassifier = data.get(config);
            for (String classifier : CLASSIFIERS) {
                List<Double> values = byClassifier != null ? byClassifier.get(classifier) : null;
                if (values == null || values.isEmpty()) {
                    AppLogger.warn("Nessun dato per " + metricName + " / " + config + " / " + classifier);
                    continue;
                }
                dataset.add(values, classifier, config);
            }
        }

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
                metricName + " per configurazione e classificatore — Syncope",
                "Configurazione",
                metricName,
                dataset,
                true
        );

        // sfondo bianco ovunque (chart esterno + area del plot)
        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);

        // solo griglie orizzontali, leggere e tratteggiate (come nella versione Python)
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(new Color(200, 200, 200));
        plot.setRangeGridlineStroke(new BasicStroke(
                1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f,
                new float[]{4.0f, 4.0f}, 0.0f));

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 1.0);

        BoxAndWhiskerRenderer renderer = (BoxAndWhiskerRenderer) plot.getRenderer();
        renderer.setFillBox(true);
        renderer.setMeanVisible(false);   // <-- toglie il pallino nero della media
        renderer.setMedianVisible(true);  // tiene la lineetta della mediana dentro il box
        for (int i = 0; i < SERIES_COLORS.length; i++) {
            renderer.setSeriesPaint(i, SERIES_COLORS[i]);
            renderer.setSeriesOutlinePaint(i, SERIES_COLORS[i].darker());
        }

        ChartUtils.saveChartAsPNG(new File(outputPath), chart, 1400, 700);
        AppLogger.info("Salvato: " + outputPath);
    }

    private static String identifyClassifier(String scheme, String schemeOptions) {
        String combined = scheme + " " + schemeOptions;
        if (combined.contains(CLASSIFIER_RANDOM_FOREST)) return CLASSIFIER_RANDOM_FOREST;
        if (combined.contains(CLASSIFIER_NAIVE_BAYES)) return CLASSIFIER_NAIVE_BAYES;
        if (combined.contains("." + CLASSIFIER_IBK)) return CLASSIFIER_IBK;
        return CLASSIFIER_UNKNOWN;
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
                    throw new IllegalStateException("Riga con formato inatteso in " + path + ": " + line);
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) row.put(headers[i], fields[i]);
                result.add(row);
            }
        }
        return result;
    }
}