package it.uniroma2.isw2.weka;

import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.unsupervised.attribute.Remove;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Calcola NPofB20 su 10 ripetizioni di 10-fold cross-validation per
 * ciascuna delle 12 configurazioni (4 pipeline x 3 classificatori),
 * usando l'API Java di Weka.
 *
 * Ogni ripetizione produce un valore di NPofB20 calcolato sull'intero
 * dataset (predizioni out-of-sample combinate dai 10 fold di quella
 * ripetizione).
 */
public class NPofB20DistributionCalculator {

    private static final String MASTER_ARFF_PATH = "weka-data/dataset_SYNCOPE_master.arff";
    private static final String SIZES_PATH = "weka-data/class_size.csv";
    private static final String OUTPUT_PATH = "weka-data/npofb20_distribution.csv";

    private static final int NUM_REPETITIONS = 10;
    private static final int NUM_FOLDS = 10;
    private static final double BUDGET_FRACTION = 0.20;
    private static final double RESAMPLE_BIAS = 1.0;
    private static final double RESAMPLE_SIZE_PERCENT = 186.88;
    private static final int INFO_GAIN_TOP_N = 6;

    public static void main(String[] args) throws Exception {
        Map<String, Integer> sizes = readSizes(SIZES_PATH);
        System.out.println("Dimensioni classi lette: " + sizes.size());

        DataSource source = new DataSource(MASTER_ARFF_PATH);
        Instances data = source.getDataSet();
        data.setClassIndex(data.numAttributes() - 1);
        System.out.println("Istanze lette: " + data.numInstances());

        int yesIndex = data.classAttribute().indexOfValue("Yes");
        if (yesIndex < 0) {
            throw new IllegalStateException("Valore 'Yes' non trovato nell'attributo classe");
        }

        String[] configs = {"Baseline", "Solo FS", "Solo Balancing", "FS + Balancing"};

        try (FileWriter writer = new FileWriter(OUTPUT_PATH)) {
            writer.write("Configurazione,Classificatore,Repetition,NPofB20\n");

            for (String config : configs) {
                for (String classifierName : new String[]{"RandomForest", "NaiveBayes", "IBk"}) {
                    System.out.println("=== " + config + " / " + classifierName + " ===");

                    for (int rep = 0; rep < NUM_REPETITIONS; rep++) {
                        int seed = rep + 1;
                        double npofb20 = runOneRepetition(data, config, classifierName, seed, yesIndex, sizes);

                        writer.write(String.format(Locale.US, "%s,%s,%d,%.4f%n",
                                config, classifierName, rep + 1, npofb20));
                        writer.flush();
                        System.out.printf("  ripetizione %d/%d -> NPofB20 = %.2f%%%n", rep + 1, NUM_REPETITIONS, npofb20);
                    }
                }
            }
        }
        System.out.println("Fatto. Risultati in " + OUTPUT_PATH);
    }

    private static double runOneRepetition(Instances data, String config, String classifierName,
                                           int seed, int yesIndex, Map<String, Integer> sizes) throws Exception {
        Random rand = new Random(seed);
        Instances randData = new Instances(data);
        randData.randomize(rand);
        randData.stratify(NUM_FOLDS);

        List<double[]> predictions = new ArrayList<>(); // [probYes, actualYes(0/1), size]

        for (int fold = 0; fold < NUM_FOLDS; fold++) {
            Instances train = randData.trainCV(NUM_FOLDS, fold);
            Instances test = randData.testCV(NUM_FOLDS, fold);

            Classifier classifier = buildClassifier(config, classifierName, seed);
            classifier.buildClassifier(train);

            for (int i = 0; i < test.numInstances(); i++) {
                Instance instance = test.instance(i);
                double[] distribution = classifier.distributionForInstance(instance);
                double probYes = distribution[yesIndex];

                int release = (int) instance.value(1);
                String className = instance.stringValue(2);
                Integer size = sizes.get(release + "#" + className);
                if (size == null) {
                    throw new IllegalStateException("Dimensione mancante per " + release + "#" + className);
                }

                double actualYes = instance.classValue() == yesIndex ? 1.0 : 0.0;
                predictions.add(new double[]{probYes, actualYes, size});
            }
        }

        return computeNPofB20(predictions);
    }

    private static double computeNPofB20(List<double[]> predictions) {
        long totalLoc = 0;
        long totalBugs = 0;
        for (double[] p : predictions) {
            totalLoc += (long) p[2];
            if (p[1] == 1.0) totalBugs++;
        }
        double budget = BUDGET_FRACTION * totalLoc;

        predictions.sort((a, b) -> Double.compare(b[0] / b[2], a[0] / a[2])); // densità decrescente

        long cumulativeLoc = 0;
        long bugsFound = 0;
        for (double[] p : predictions) {
            double size = p[2];
            if (cumulativeLoc + size > budget) break;
            cumulativeLoc += size;
            if (p[1] == 1.0) bugsFound++;
        }
        return 100.0 * bugsFound / totalBugs;
    }

    private static Classifier buildClassifier(String config, String classifierName, int seed) throws Exception {
        Classifier base = buildBaseClassifier(classifierName, seed);

        switch (config) {
            case "Baseline": {
                FilteredClassifier fc = new FilteredClassifier();
                fc.setFilter(buildRemoveFilter());
                fc.setClassifier(base);
                return fc;
            }
            case "Solo FS": {
                FilteredClassifier fc = new FilteredClassifier();
                fc.setFilter(buildRemoveFilter());
                fc.setClassifier(buildAttributeSelectedClassifier(base));
                return fc;
            }
            case "Solo Balancing": {
                FilteredClassifier fc = new FilteredClassifier();
                MultiFilter mf = new MultiFilter();
                mf.setFilters(new Filter[]{buildRemoveFilter(), buildResampleFilter(seed)});
                fc.setFilter(mf);
                fc.setClassifier(base);
                return fc;
            }
            case "FS + Balancing": {
                FilteredClassifier fc = new FilteredClassifier();
                MultiFilter mf = new MultiFilter();
                mf.setFilters(new Filter[]{buildRemoveFilter(), buildAttributeSelectionFilter(), buildResampleFilter(seed)});
                fc.setFilter(mf);
                fc.setClassifier(base);
                return fc;
            }
            default:
                throw new IllegalArgumentException("Configurazione sconosciuta: " + config);
        }
    }

    private static Classifier buildBaseClassifier(String name, int seed) {
        switch (name) {
            case "RandomForest": {
                RandomForest rf = new RandomForest();
                rf.setSeed(seed); // stesso comportamento del -S visto nelle run Weka precedenti
                return rf;
            }
            case "NaiveBayes":
                return new NaiveBayes();
            case "IBk":
                return new IBk(); // K=1 di default, coerente con le run GUI
            default:
                throw new IllegalArgumentException("Classificatore sconosciuto: " + name);
        }
    }

    private static Remove buildRemoveFilter() {
        Remove remove = new Remove();
        remove.setAttributeIndices("1-3"); // Project, Release, ClassName
        return remove;
    }

    private static AttributeSelectedClassifier buildAttributeSelectedClassifier(Classifier base) {
        AttributeSelectedClassifier asc = new AttributeSelectedClassifier();
        asc.setEvaluator(new InfoGainAttributeEval());
        Ranker ranker = new Ranker();
        ranker.setNumToSelect(INFO_GAIN_TOP_N);
        asc.setSearch(ranker);
        asc.setClassifier(base);
        return asc;
    }

    private static AttributeSelection buildAttributeSelectionFilter() {
        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(new InfoGainAttributeEval());
        Ranker ranker = new Ranker();
        ranker.setNumToSelect(INFO_GAIN_TOP_N);
        filter.setSearch(ranker);
        return filter;
    }

    private static Resample buildResampleFilter(int seed) {
        Resample resample = new Resample();
        resample.setBiasToUniformClass(RESAMPLE_BIAS);
        resample.setSampleSizePercent(RESAMPLE_SIZE_PERCENT);
        resample.setRandomSeed(seed);
        resample.setNoReplacement(false);
        return resample;
    }

    private static Map<String, Integer> readSizes(String path) throws IOException {
        Map<String, Integer> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine(); // salta header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = splitCsvLine(line);
                result.put(fields[0] + "#" + fields[1], Integer.parseInt(fields[2]));
            }
        }
        return result;
    }

    private static String[] splitCsvLine(String line) {
        String[] raw = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        String[] cleaned = new String[raw.length];
        for (int i = 0; i < raw.length; i++) cleaned[i] = raw[i].trim().replaceAll("^\"|\"$", "");
        return cleaned;
    }
}