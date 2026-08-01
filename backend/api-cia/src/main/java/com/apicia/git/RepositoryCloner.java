package com.apicia.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RepositoryCloner {

    public Path cloneRepository(String repositoryUrl) {

        try {

            Path tempDirectory = Files.createTempDirectory("api-cia-");

            Git.cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(tempDirectory.toFile())
                    .call();

            return tempDirectory;

        } catch (IOException | GitAPIException e) {

            throw new RuntimeException(
                    "Failed to clone repository: " + repositoryUrl,
                    e
            );
        }
    }
}