package it.uniroma2.isw2.model;

public class LabeledClassRelease {

    private final int releaseId;
    private final String classPath;
    private final boolean buggy;

    public LabeledClassRelease(int releaseId, String classPath, boolean buggy) {
        this.releaseId = releaseId;
        this.classPath = classPath;
        this.buggy = buggy;
    }

    public int getReleaseId() {
        return releaseId;
    }

    public String getClassPath() {
        return classPath;
    }

    public boolean isBuggy() {
        return buggy;
    }
}