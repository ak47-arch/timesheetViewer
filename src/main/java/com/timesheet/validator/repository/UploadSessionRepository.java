package com.timesheet.validator.repository;
import com.timesheet.validator.domain.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.Optional;
public interface UploadSessionRepository extends JpaRepository<UploadSession,Long> {
    Optional<UploadSession> findBySessionId(String sessionId);
    List<UploadSession> findAllByOrderByUploadedAtDesc();
}
