package com.mindlink.repository;

import com.mindlink.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByTargetTypeAndTargetId(Report.TargetType targetType, Long targetId);
    List<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
}
