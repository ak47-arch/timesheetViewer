package com.timesheet.validator.repository;
import com.timesheet.validator.domain.ValidationIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ValidationIssueRepository extends JpaRepository<ValidationIssue,Long> {
    List<ValidationIssue> findBySessionId(String sessionId);
    List<ValidationIssue> findBySessionIdAndSeverity(String sessionId, String severity);
    long countBySessionIdAndSeverity(String sessionId, String severity);
    long countBySessionIdAndSheetNameAndSeverity(String sessionId, String sheetName, String severity);
    void deleteBySessionId(String sessionId);
}
