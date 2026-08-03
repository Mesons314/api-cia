package com.apicia.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "spec_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_label", length = 50)
    private String versionLabel;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "total_endpoints")
    private Integer totalEndpoints;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
