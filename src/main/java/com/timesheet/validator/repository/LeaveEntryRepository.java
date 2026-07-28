package com.timesheet.validator.repository;

import com.timesheet.validator.domain.LeaveEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveEntryRepository extends JpaRepository<LeaveEntry, Long> {

    /** Everyone's leaves within the visible calendar window (team days-off panel). */
    List<LeaveEntry> findByLeaveDateBetweenOrderByLeaveDateAsc(LocalDate from, LocalDate to);

    /** One resource's leaves within a window (the signed-in user's own leaves). */
    List<LeaveEntry> findByResourceIdAndLeaveDateBetweenOrderByLeaveDateAsc(
            String resourceId, LocalDate from, LocalDate to);

    /** Dedupe guard: a resource has at most one leave per day per source. */
    boolean existsByResourceIdAndLeaveDateAndSource(
            String resourceId, LocalDate leaveDate, String source);

    Optional<LeaveEntry> findByResourceIdAndLeaveDateAndSource(
            String resourceId, LocalDate leaveDate, String source);

    /** Used before re-importing an admin planner so the import is idempotent. */
    void deleteBySource(String source);
}
