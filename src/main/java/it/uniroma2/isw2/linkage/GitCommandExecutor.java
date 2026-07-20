package it.uniroma2.isw2.linkage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GitCommandExecutor {

    private final String repositoryPath;

    public GitCommandExecutor(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public List<String> execute(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(repositoryPath));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Comando Git fallito (exit " + exitCode + "): " + String.join(" ", command));
        }
        return output;
    }
}