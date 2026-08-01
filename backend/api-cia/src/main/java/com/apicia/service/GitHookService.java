package com.apicia.service;

import com.apicia.git.RepositoryCloner;
import com.apicia.model.dto.GitHookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class GitHookService {

    private static final Logger logger = LoggerFactory.getLogger(GitHookService.class);

    private final RepositoryCloner repositoryCloner;

    public GitHookService(RepositoryCloner repositoryCloner) {
        this.repositoryCloner = repositoryCloner;
    }

    public String processGitHook(GitHookRequest request) {

        logger.info("========== Git Hook Triggered ==========");
        logger.info("Repository : {}", request.getRepository());
        logger.info("Branch     : {}", request.getBranch());
        logger.info("Before SHA : {}", request.getBefore());
        logger.info("After SHA  : {}", request.getAfter());
        logger.info("Local Path : {}", request.getLocalPath());

        try {

            Path clonedRepository = repositoryCloner.cloneRepository(request.getRepository());

            logger.info("Repository cloned successfully.");
            logger.info("Cloned Repository Path : {}", clonedRepository);

        } catch (Exception e) {

            logger.error("Repository cloning failed.", e);

            return "Repository Clone Failed";
        }

        logger.info("========================================");

        return "Git Hook Trigger Received Successfully";
    }
}