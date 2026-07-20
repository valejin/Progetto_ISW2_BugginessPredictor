package it.uniroma2.isw2.linkage;

import it.uniroma2.isw2.model.ReleaseClassEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReleaseClassInventory {

    private final GitCommandExecutor git;

    public ReleaseClassInventory(GitCommandExecutor git) {
        this.git = git;
    }

    public List<ReleaseClassEntry> buildInventory(Map<Integer, String> snapshotCommitsByReleaseId)
            throws IOException, InterruptedException {
        List<ReleaseClassEntry> result = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : snapshotCommitsByReleaseId.entrySet()) {
            int releaseId = entry.getKey();
            String commitHash = entry.getValue();

            List<String> trackedFiles = git.execute("git", "ls-tree", "-r", "--name-only", commitHash);

            for (String path : trackedFiles) {
                String normalized = path.trim().replace("\\", "/");
                if (isProductionClass(normalized)) {
                    result.add(new ReleaseClassEntry(releaseId, normalized));
                }
            }
        }
        return result;
    }

    private boolean isProductionClass(String path) {
        return path.endsWith(".java")
                && path.contains("/src/main/java/")
                && !path.contains("/src/test/java/")
                && !path.startsWith("fit/")
                && !path.contains("/fit/");
    }
}