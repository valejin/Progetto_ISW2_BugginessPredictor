package it.uniroma2.isw2.model;

public class BuggyClassEntry {

    private final String ticketId;
    private final String commitHash;
    private final String classPath;

    public BuggyClassEntry(String ticketId, String commitHash, String classPath) {
        this.ticketId = ticketId;
        this.commitHash = commitHash;
        this.classPath = classPath;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getClassPath() {
        return classPath;
    }
}