package it.uniroma2.isw2.model;

public class FileTouch {

    private final String commitHash;
    private final long epochSeconds;
    private final String authorEmail;
    private final int linesAdded;
    private final int linesDeleted;

    public FileTouch(String commitHash, long epochSeconds, String authorEmail, int linesAdded, int linesDeleted) {
        this.commitHash = commitHash;
        this.epochSeconds = epochSeconds;
        this.authorEmail = authorEmail;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
    }

    public String getCommitHash() { return commitHash; }
    public long getEpochSeconds() { return epochSeconds; }
    public String getAuthorEmail() { return authorEmail; }
    public int getLinesAdded() { return linesAdded; }
    public int getLinesDeleted() { return linesDeleted; }
}