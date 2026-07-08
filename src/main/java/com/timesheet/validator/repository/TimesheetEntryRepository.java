package com.timesheet.validator.repository;

import com.timesheet.validator.domain.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {

    List<TimesheetEntry> findBySubmittedByOrderByEntryDateDescSubmittedAtDesc(String submittedBy);

    List<TimesheetEntry> findAllByOrderByEntryDateDescSubmittedAtDesc();

    /** All entries for a specific resource+date — used to show day's task list */
    List<TimesheetEntry> findByResourceNameAndEntryDateOrderBySubmittedAtAsc(
            String resourceName, LocalDate entryDate);

    /** Total hours already logged by this resource on a given date */
    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimesheetEntry t " +
           "WHERE t.resourceName = :name AND t.entryDate = :date")
    BigDecimal sumHoursByResourceAndDate(@Param("name") String name, @Param("date") LocalDate date);

    /** Grouped day summary — for the weekly calendar view */
    @Query("SELECT t.entryDate, SUM(t.hours), COUNT(t) FROM TimesheetEntry t " +
           "WHERE t.submittedBy = :username " +
           "GROUP BY t.entryDate ORDER BY t.entryDate DESC")
    List<Object[]> dailySummaryByUser(@Param("username") String username);
}
