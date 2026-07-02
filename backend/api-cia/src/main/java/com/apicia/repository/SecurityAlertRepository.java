package com.apicia.repository;

import com.apicia.model.entity.AlertSeverity;
import com.apicia.model.entity.SecurityAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    List<SecurityAlert> findByReportId(Long reportId);

    List<SecurityAlert> findByReportIdAndSeverity(Long reportId, AlertSeverity severity);
}
