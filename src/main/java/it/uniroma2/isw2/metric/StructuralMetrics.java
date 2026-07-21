package it.uniroma2.isw2.model;

public class StructuralMetrics {

    private final int nom;
    private final int fanOut;
    private final int cyclomaticComplexity;

    public StructuralMetrics(int nom, int fanOut, int cyclomaticComplexity) {
        this.nom = nom;
        this.fanOut = fanOut;
        this.cyclomaticComplexity = cyclomaticComplexity;
    }

    public int getNom() {
        return nom;
    }

    public int getFanOut() {
        return fanOut;
    }

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }
}