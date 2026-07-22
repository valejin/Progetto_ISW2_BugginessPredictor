package it.uniroma2.isw2.model;

public class CommitStats {

    private final int fileCount;
    private final int directoryCount;
    private final int subsystemCount;

    public CommitStats(int fileCount, int directoryCount, int subsystemCount) {
        this.fileCount = fileCount;
        this.directoryCount = directoryCount;
        this.subsystemCount = subsystemCount;
    }

    public int getFileCount() { return fileCount; }
    public int getDirectoryCount() { return directoryCount; }
    public int getSubsystemCount() { return subsystemCount; }
}