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

    private ProcessMetrics(Builder builder) {
        this.locTouched = builder.locTouched;
        this.nFix = builder.nFix;
        this.nAuth = builder.nAuth;
        this.locAdded = builder.locAdded;
        this.maxLocAdded = builder.maxLocAdded;
        this.churn = builder.churn;
        this.maxChurn = builder.maxChurn;
        this.ns = builder.ns;
        this.nd = builder.nd;
        this.changeSetSize = builder.changeSetSize;
        this.ageDays = builder.ageDays;
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

    public static Builder builder() {
        return new Builder();
    }

    /** Builder: evita un costruttore con 11 parametri posizionali (rule java:S107). */
    public static final class Builder {
        private int locTouched;
        private int nFix;
        private int nAuth;
        private int locAdded;
        private int maxLocAdded;
        private int churn;
        private int maxChurn;
        private int ns;
        private int nd;
        private int changeSetSize;
        private long ageDays;

        private Builder() {
        }

        public Builder locTouched(int locTouched) {
            this.locTouched = locTouched;
            return this;
        }

        public Builder nFix(int nFix) {
            this.nFix = nFix;
            return this;
        }

        public Builder nAuth(int nAuth) {
            this.nAuth = nAuth;
            return this;
        }

        public Builder locAdded(int locAdded) {
            this.locAdded = locAdded;
            return this;
        }

        public Builder maxLocAdded(int maxLocAdded) {
            this.maxLocAdded = maxLocAdded;
            return this;
        }

        public Builder churn(int churn) {
            this.churn = churn;
            return this;
        }

        public Builder maxChurn(int maxChurn) {
            this.maxChurn = maxChurn;
            return this;
        }

        public Builder ns(int ns) {
            this.ns = ns;
            return this;
        }

        public Builder nd(int nd) {
            this.nd = nd;
            return this;
        }

        public Builder changeSetSize(int changeSetSize) {
            this.changeSetSize = changeSetSize;
            return this;
        }

        public Builder ageDays(long ageDays) {
            this.ageDays = ageDays;
            return this;
        }

        public ProcessMetrics build() {
            return new ProcessMetrics(this);
        }
    }
}