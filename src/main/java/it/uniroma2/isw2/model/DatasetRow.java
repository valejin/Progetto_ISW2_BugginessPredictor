package it.uniroma2.isw2.model;

public class DatasetRow {

    private final String project;
    private final int release;
    private final String className;
    private final StructuralMetrics structuralMetrics;
    private final ProcessMetrics processMetrics;
    private final int nSmells;
    private final boolean buggy;

    public DatasetRow(String project, int release, String className,
                      StructuralMetrics structuralMetrics, ProcessMetrics processMetrics,
                      int nSmells, boolean buggy) {
        this.project = project;
        this.release = release;
        this.className = className;
        this.structuralMetrics = structuralMetrics;
        this.processMetrics = processMetrics;
        this.nSmells = nSmells;
        this.buggy = buggy;
    }

    public String toCsvRow() {
        return String.join(",",
                project,
                String.valueOf(release),
                "\"" + className + "\"",
                String.valueOf(structuralMetrics.getNom()),
                String.valueOf(structuralMetrics.getFanOut()),
                String.valueOf(structuralMetrics.getCyclomaticComplexity()),
                String.valueOf(processMetrics.getLocTouched()),
                String.valueOf(processMetrics.getNFix()),
                String.valueOf(processMetrics.getNAuth()),
                String.valueOf(processMetrics.getLocAdded()),
                String.valueOf(processMetrics.getMaxLocAdded()),
                String.valueOf(processMetrics.getChurn()),
                String.valueOf(processMetrics.getMaxChurn()),
                String.valueOf(processMetrics.getNs()),
                String.valueOf(processMetrics.getNd()),
                String.valueOf(processMetrics.getChangeSetSize()),
                String.valueOf(processMetrics.getAgeDays()),
                String.valueOf(nSmells),
                buggy ? "Yes" : "No"
        );
    }

    public static String csvHeader() {
        return "Project,Release,ClassName,NOM,FanOut,CyclomaticComplexity,LOCTouched,NFix,NAuth,"
                + "LOCAdded,MaxLOCAdded,Churn,MaxChurn,NS,ND,ChangeSetSize,Age,NSmells,Bugginess";
    }
}