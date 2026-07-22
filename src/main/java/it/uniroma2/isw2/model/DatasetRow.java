package it.uniroma2.isw2.model;

public class DatasetRow {

    private final String project;
    private final int release;
    private final String className;
    private final int nom;
    private final int fanOut;
    private final int cyclomaticComplexity;
    private final int locTouched;
    private final int nFix;
    private final int nAuth;
    private final int locAdded;
    private final int maxLocAdded;
    private final int churn;
    private final int maxChurn;
    private final int ns;
    private final int nd;
    private final int changeSetSize;
    private final long age;
    private final int nSmells;
    private final boolean buggy;

    public DatasetRow(String project, int release, String className,
                      int nom, int fanOut, int cyclomaticComplexity,
                      int locTouched, int nFix, int nAuth, int locAdded, int maxLocAdded,
                      int churn, int maxChurn, int ns, int nd, int changeSetSize, long age,
                      int nSmells, boolean buggy) {
        this.project = project;
        this.release = release;
        this.className = className;
        this.nom = nom;
        this.fanOut = fanOut;
        this.cyclomaticComplexity = cyclomaticComplexity;
        this.locTouched = locTouched;
        this.nFix = nFix;
        this.nAuth = nAuth;
        this.locAdded = locAdded;
        this.maxLocAdded = maxLocAdded;
        this.churn = churn;
        this.maxChurn = maxChurn;
        this.ns = ns;
        this.nd = nd;
        this.changeSetSize = changeSetSize;
        this.age = age;
        this.nSmells = nSmells;
        this.buggy = buggy;
    }

    public String toCsvRow() {
        return String.join(",",
                project,
                String.valueOf(release),
                "\"" + className + "\"",
                String.valueOf(nom),
                String.valueOf(fanOut),
                String.valueOf(cyclomaticComplexity),
                String.valueOf(locTouched),
                String.valueOf(nFix),
                String.valueOf(nAuth),
                String.valueOf(locAdded),
                String.valueOf(maxLocAdded),
                String.valueOf(churn),
                String.valueOf(maxChurn),
                String.valueOf(ns),
                String.valueOf(nd),
                String.valueOf(changeSetSize),
                String.valueOf(age),
                String.valueOf(nSmells),
                buggy ? "Yes" : "No"
        );
    }

    public static String csvHeader() {
        return "Project,Release,ClassName,NOM,FanOut,CyclomaticComplexity,LOCTouched,NFix,NAuth,"
                + "LOCAdded,MaxLOCAdded,Churn,MaxChurn,NS,ND,ChangeSetSize,Age,NSmells,Bugginess";
    }
}