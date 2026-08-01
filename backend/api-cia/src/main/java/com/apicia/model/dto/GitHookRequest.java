package com.apicia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitHookRequest {

    private String repository;

    private String branch;

    private String before;

    private String after;
}