package it.uniroma2.isw2.proportion;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AffectedVersionIvResolver {

    private final Map<String, Release> releaseByName;

    public AffectedVersionIvResolver(List<Release> allReleases) {
        this.releaseByName = allReleases.stream()
                .collect(Collectors.toMap(Release::getName, r -> r));
    }

    /**
     * Prova a determinare la IV dalle Affected Version dichiarate su Jira.
     * Vuoto se: nessuna AV, nessuna AV risolvibile a una release nota,
     * o la IV risultante è incoerente (> OV) -> il ticket cadrà su Proportion.
     */
    public Optional<Integer> resolveIv(Ticket ticket) {
        if (!ticket.hasAffectedVersions() || ticket.getOpeningVersion() == null) {
            return Optional.empty();
        }

        Optional<Integer> earliestKnownAv = ticket.getAffectedVersionNames().stream()
                .map(releaseByName::get)
                .filter(Objects::nonNull)
                .map(Release::getId)
                .min(Comparator.naturalOrder());

        if (earliestKnownAv.isEmpty()) {
            return Optional.empty(); // nessuna AV tra quelle note
        }

        int candidateIv = earliestKnownAv.get();
        return candidateIv <= ticket.getOpeningVersion()
                ? Optional.of(candidateIv)
                : Optional.empty(); // IV incoerente: iniettato dopo l'apertura del ticket
    }
}