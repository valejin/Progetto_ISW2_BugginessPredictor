package it.uniroma2.isw2.model;

import java.util.Objects;

public class FixCommit {

    private final String ticketId;
    private final String commitHash;
    private final long commitEpochSeconds;

    public FixCommit(String ticketId, String commitHash, long commitEpochSeconds) {
        this.ticketId = ticketId;
        this.commitHash = commitHash;
        this.commitEpochSeconds = commitEpochSeconds;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public long getCommitEpochSeconds() {
        return commitEpochSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixCommit other)) return false;
        return ticketId.equals(other.ticketId) && commitHash.equals(other.commitHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId, commitHash);
    }
}