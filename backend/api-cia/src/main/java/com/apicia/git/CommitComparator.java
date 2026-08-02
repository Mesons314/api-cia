package com.apicia.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommitComparator {

    private static final Logger logger =
            LoggerFactory.getLogger(CommitComparator.class);

    public void compareCommits(String before, String after) {

        logger.info("========== Comparing Commits ==========");
        logger.info("Before Commit : {}", before);
        logger.info("After Commit  : {}", after);
        logger.info("=======================================");

    }
}