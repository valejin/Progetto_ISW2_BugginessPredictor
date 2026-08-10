package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.model.CommitStats;
import it.uniroma2.isw2.model.FileTouch;
import it.uniroma2.isw2.model.ProcessMetrics;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessMetricsCalculator {

    private final Set<String> fixCommitHashes;

    public ProcessMetricsCalculator(Set<String> fixCommitHashes) {
        this.fixCommitHashes = fixCommitHashes;
    }

    public ProcessMetrics compute(List<FileTouch> touches,
                                  Map<String, CommitStats> commitStatsForRelease,
                                  LocalDateTime windowStart,
                                  LocalDateTime windowEnd) {

        long windowEndEpoch = toEpoch(windowEnd);
        Long windowStartEpoch = windowStart == null ? null : toEpoch(windowStart);

        int locTouched = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = 0;
        int ns = 0;
        int nd = 0;
        int changeSetSize = 0;
        int nFix = 0;
        Set<String> authorsUpToRelease = new HashSet<>();
        long firstTouchEpoch = Long.MAX_VALUE;

        for (FileTouch t : touches) {
            if (t.getEpochSeconds() > windowEndEpoch) {
                continue;
            }
            firstTouchEpoch = Math.min(firstTouchEpoch, t.getEpochSeconds());

            authorsUpToRelease.add(t.getAuthorEmail());
            if (fixCommitHashes.contains(t.getCommitHash())) {
                nFix++;
            }

            boolean inWindow = windowStartEpoch == null || t.getEpochSeconds() > windowStartEpoch;
            if (inWindow) {
                int added = t.getLinesAdded();
                int deleted = t.getLinesDeleted();
                int commitChurn = added - deleted;

                locTouched += added + deleted;
                locAdded += added;
                maxLocAdded = Math.max(maxLocAdded, added);
                churn += commitChurn;
                maxChurn = Math.max(maxChurn, commitChurn);

                CommitStats stats = commitStatsForRelease.get(t.getCommitHash());
                if (stats != null) {
                    ns += stats.getSubsystemCount();
                    nd += stats.getDirectoryCount();
                    changeSetSize += stats.getFileCount();
                }
            }
        }

        long ageDays = firstTouchEpoch == Long.MAX_VALUE ? 0 : (windowEndEpoch - firstTouchEpoch) / 86400L;

        return ProcessMetrics.builder()
                .locTouched(locTouched)
                .nFix(nFix)
                .nAuth(authorsUpToRelease.size())
                .locAdded(locAdded)
                .maxLocAdded(maxLocAdded)
                .churn(churn)
                .maxChurn(maxChurn)
                .ns(ns)
                .nd(nd)
                .changeSetSize(changeSetSize)
                .ageDays(ageDays)
                .build();
    }

    private long toEpoch(LocalDateTime dt) {
        return dt.toEpochSecond(ZoneOffset.UTC);
    }
}