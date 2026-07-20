package it.uniroma2.isw2.model;

import java.util.Comparator;
import java.util.List;

public final class ReleaseSelector {

    private ReleaseSelector() {
    }

    public static List<Release> selectFirstN(List<Release> allReleases, int n) {
        if (n > allReleases.size()) {
            throw new IllegalArgumentException(
                    "Richieste " + n + " release ma ce ne sono solo " + allReleases.size());
        }
        return allReleases.stream()
                .sorted(Comparator.comparingInt(Release::getId))
                .limit(n)
                .toList();
    }
}