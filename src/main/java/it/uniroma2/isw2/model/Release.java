package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Release {

    private final int id;
    private final String jiraId;
    private final String name;
    private final LocalDateTime releaseDate;

    public Release(int id, String jiraId, String name, LocalDateTime releaseDate) {
        this.id = id;
        this.jiraId = jiraId;
        this.name = name;
        this.releaseDate = releaseDate;
    }

    public int getId() {
        return id;
    }

    public String getJiraId() {
        return jiraId;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Release)) return false;
        return id == ((Release) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Release{id=" + id + ", name='" + name + "', date=" + releaseDate + "}";
    }
}