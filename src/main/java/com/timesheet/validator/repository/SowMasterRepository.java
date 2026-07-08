package com.timesheet.validator.repository;

import com.timesheet.validator.domain.SowMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SowMasterRepository extends JpaRepository<SowMaster, Long> {
    Optional<SowMaster> findBySowNumber(String sowNumber);
    boolean existsBySowNumber(String sowNumber);
    List<SowMaster> findByActiveTrue();
}
