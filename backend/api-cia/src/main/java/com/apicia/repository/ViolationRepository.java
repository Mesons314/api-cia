package com.apicia.repository;

import com.apicia.model.entity.Violation;
import com.apicia.model.entity.ViolationSeverity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {

    List<Violation> findByReportId(Long reportId);

    List<Violation> findByReportIdAndSeverity(Long reportId, ViolationSeverity severity);
}
