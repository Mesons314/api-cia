package com.apicia.model.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecVersionDTO {
    private Long id;
    private String versionLabel;
    private String fileName;
    private Integer totalEndpoints;
    private LocalDateTime uploadedAt;
}
