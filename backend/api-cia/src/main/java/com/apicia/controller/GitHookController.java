package com.apicia.controller;

import com.apicia.model.dto.GitHookRequest;
import com.apicia.service.GitHookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analysis")
public class GitHookController {

    private final GitHookService gitHookService;

    public GitHookController(GitHookService gitHookService) {
        this.gitHookService = gitHookService;
    }

    @PostMapping("/git")
    public ResponseEntity<String> receiveGitHook(@RequestBody GitHookRequest request) {

        String response = gitHookService.processGitHook(request);

        return ResponseEntity.ok(response);
    }
}