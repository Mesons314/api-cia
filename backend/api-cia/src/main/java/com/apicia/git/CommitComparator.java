package com.apicia.git;

import org.springframework.stereotype.Component;

@Component
public class CommitComparator {

    public void compareCommits(String before, String after) {

        System.out.println("Comparing commits...");
        System.out.println("Before : " + before);
        System.out.println("After  : " + after);

    }
}