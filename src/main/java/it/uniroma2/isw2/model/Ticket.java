package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.util.List;

public class Ticket {

    private final String id;
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;
    private final List<String> affectedVersionNames; // nomi grezzi da Jira, alcuni potrebbero non esistere tra le release note

    // Popolati nello step di stima IV/OV/FV, non alla lettura del CSV
    private Integer injectedVersion;
    private Integer openingVersion;
    private Integer fixVersion;

    public Ticket(String id, LocalDateTime creationDate, LocalDateTime resolutionDate,
                  List<String> affectedVersionNames) {
        this.id = id;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.affectedVersionNames = affectedVersionNames;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }

    public List<String> getAffectedVersionNames() {
        return affectedVersionNames;
    }

    public boolean hasAffectedVersions() {
        return affectedVersionNames != null && !affectedVersionNames.isEmpty();
    }

    public Integer getInjectedVersion() {
        return injectedVersion;
    }

    public void setInjectedVersion(Integer injectedVersion) {
        this.injectedVersion = injectedVersion;
    }

    public Integer getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(Integer openingVersion) {
        this.openingVersion = openingVersion;
    }

    public Integer getFixVersion() {
        return fixVersion;
    }

    public void setFixVersion(Integer fixVersion) {
        this.fixVersion = fixVersion;
    }

    @Override
    public String toString() {
        return "Ticket{id='" + id + "', IV=" + injectedVersion
                + ", OV=" + openingVersion + ", FV=" + fixVersion + "}";
    }
}