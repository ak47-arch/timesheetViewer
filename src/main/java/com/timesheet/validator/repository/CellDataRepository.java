package com.timesheet.validator.repository;
import com.timesheet.validator.domain.CellData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CellDataRepository extends JpaRepository<CellData,Long> {
    List<CellData> findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(String sessionId, String sheetName);
    void deleteBySessionId(String sessionId);
}
