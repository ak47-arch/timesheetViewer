package com.timesheet.validator.repository;
import com.timesheet.validator.domain.SheetMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SheetMetaRepository extends JpaRepository<SheetMeta,Long> {
    List<SheetMeta> findBySessionIdOrderBySheetIndex(String sessionId);
    void deleteBySessionId(String sessionId);
}
