package com.apicia.service;

import com.apicia.model.dto.GitHookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHookService {

    private static final Logger logger = LoggerFactory.getLogger(GitHookService.class);

    public String processGitHook(GitHookRequest request) {

        logger.info("========== Git Hook Triggered ==========");
        logger.info("Repository : {}", request.getRepository());
        logger.info("Branch     : {}", request.getBranch());
        logger.info("Before SHA : {}", request.getBefore());
        logger.info("After SHA  : {}", request.getAfter());
        logger.info("========================================");

        return "Git Hook Trigger Received Successfully";
    }
}