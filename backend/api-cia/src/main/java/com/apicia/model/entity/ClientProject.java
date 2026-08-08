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
@Table(name = "client_projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", unique = true, nullable = false, length = 100)
    private String projectName;

    @Column(name = "source_path", nullable = false, length = 500)
    private String sourcePath;

    @CreationTimestamp
    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;
}
