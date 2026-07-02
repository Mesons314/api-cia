package com.apicia.repository;

import com.apicia.model.entity.AnalysisReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    List<AnalysisReport> findAllByOrderByCreatedAtDesc();

    List<AnalysisReport> findByOldSpecIdOrNewSpecId(Long oldId, Long newId);
}
