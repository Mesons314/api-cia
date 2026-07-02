package com.apicia.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "violations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private AnalysisReport report;

    @Column(name = "rule_id", length = 50)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 50)
    private ViolationSeverity severity;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;
}
