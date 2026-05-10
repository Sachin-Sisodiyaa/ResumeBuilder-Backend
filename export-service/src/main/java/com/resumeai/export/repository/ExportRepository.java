package com.resumeai.export.repository;

import com.resumeai.export.model.ExportJob;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportRepository extends JpaRepository<ExportJob, String> {

    List<ExportJob> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<ExportJob> findByExpiresAtBeforeAndStatus(Instant cutoff, String status);

    List<ExportJob> findByRequestedAtBefore(Instant cutoff);

    long countByUserIdAndFormatAndRequestedAtAfter(Long userId, String format, Instant since);

    long countByStatus(String status);

    long countByFormat(String format);
}
