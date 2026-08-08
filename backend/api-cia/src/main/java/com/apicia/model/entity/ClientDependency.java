package com.apicia.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "client_dependencies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_project_id", nullable = false)
    private ClientProject clientProject;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "line_number")
    private int lineNumber;

    @Column(name = "http_method", nullable = false, length = 20)
    private String httpMethod;

    @Column(name = "raw_path", nullable = false, length = 500)
    private String rawPath;

    @Column(name = "normalized_path", nullable = false, length = 500)
    private String normalizedPath;
}
