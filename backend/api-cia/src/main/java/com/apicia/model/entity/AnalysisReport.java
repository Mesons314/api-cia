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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "analysis_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_spec_id")
    private SpecVersion oldSpec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_spec_id")
    private SpecVersion newSpec;

    @Column(name = "d_struct")
    private Double dStruct;

    @Column(name = "d_api")
    private Double dApi;

    @Column(name = "a_sec")
    private Double aSec;

    @Column(name = "s_total")
    private Double sTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 50)
    private RiskLevel riskLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
