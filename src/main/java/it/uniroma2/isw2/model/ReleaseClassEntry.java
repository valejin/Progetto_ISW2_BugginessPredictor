package it.uniroma2.isw2.model;

import java.util.Objects;

public class ReleaseClassEntry {

    private final int releaseId;
    private final String classPath;

    public ReleaseClassEntry(int releaseId, String classPath) {
        this.releaseId = releaseId;
        this.classPath = classPath;
    }

    public int getReleaseId() {
        return releaseId;
    }

    public String getClassPath() {
        return classPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReleaseClassEntry other)) return false;
        return releaseId == other.releaseId && classPath.equals(other.classPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseId, classPath);
    }
}