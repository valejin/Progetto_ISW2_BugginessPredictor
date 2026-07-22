package it.uniroma2.isw2.model;

public class ProcessMetrics {

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
    private final long ageDays;

    public ProcessMetrics(int locTouched, int nFix, int nAuth, int locAdded, int maxLocAdded,
                          int churn, int maxChurn, int ns, int nd, int changeSetSize, long ageDays) {
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
        this.ageDays = ageDays;
    }

    public int getLocTouched() { return locTouched; }
    public int getNFix() { return nFix; }
    public int getNAuth() { return nAuth; }
    public int getLocAdded() { return locAdded; }
    public int getMaxLocAdded() { return maxLocAdded; }
    public int getChurn() { return churn; }
    public int getMaxChurn() { return maxChurn; }
    public int getNs() { return ns; }
    public int getNd() { return nd; }
    public int getChangeSetSize() { return changeSetSize; }
    public long getAgeDays() { return ageDays; }
}