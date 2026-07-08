package com.timesheet.validator.service;

import com.timesheet.validator.config.AppProperties;
import com.timesheet.validator.domain.*;
import com.timesheet.validator.dto.TimesheetEntryForm;
import com.timesheet.validator.repository.*;
import com.timesheet.validator.domain.SowMaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetEntryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TimesheetEntryRepository entryRepo;
    private final PublicHolidayRepository  holidayRepo;
    private final ResourceRepository       resourceRepo;
    private final SowMasterRepository      sowRepo;
    private final ResourceSowRepository    resourceSowRepo;
    private final AppProperties            props;

    public LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), FMT); }
        catch (Exception e) { return null; }
    }

    /**
     * Hours already logged by this resource on a given date.
     * Used both for validation and for the live day tracker in the UI.
     */
    public BigDecimal getLoggedHours(String resourceName, LocalDate date) {
        BigDecimal v = entryRepo.sumHoursByResourceAndDate(resourceName, date);
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * All tasks entered for a specific resource+date — shown in the day task list.
     */
    public List<TimesheetEntry> getTasksForDay(String resourceName, LocalDate date) {
        return entryRepo.findByResourceNameAndEntryDateOrderBySubmittedAtAsc(resourceName, date);
    }

    /**
     * Validates one form entry against all 7 business rules.
     * Returns errors — empty list = safe to save.
     */
    public List<String> validate(TimesheetEntryForm form, String resourceName) {
        List<String> errors = new ArrayList<>();
        LocalDate date = parseDate(form.getEntryDate());
        if (date == null) {
            errors.add("Date is invalid or missing");
            return errors;
        }

        double max         = props.getValidation().getMaxHoursPerDay();
        String expectedSow = props.getValidation().getExpectedSow();

        // TS-02: Weekends — override requires checkbox + reason
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            if (!form.isWeekendOverride()) {
                errors.add("TS-02: Weekend entry not allowed. Tick the override checkbox and provide a reason.");
            } else if (form.getWeekendOverrideReason() == null || form.getWeekendOverrideReason().isBlank()) {
                errors.add("TS-02: Weekend override requires a mandatory reason.");
            }
        }

        // TS-03: Public holidays — override does NOT bypass this
        Set<LocalDate> holidays = holidayRepo.findAll().stream()
                .filter(h -> h.isActive())
                .map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());
        if (holidays.contains(date)) {
            String hName = holidayRepo.findAll().stream()
                    .filter(h -> h.getHolidayDate().equals(date))
                    .map(PublicHoliday::getHolidayName).findFirst().orElse("public holiday");
            errors.add("TS-03: Public holiday — " + hName + " (" + date + "). Cannot log time.");
        }

        // TS-04: Hours > 0
        if (form.getHours() == null || form.getHours().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("TS-04: Hours must be greater than 0");
        }

        // TS-01: Cumulative daily cap across all tasks for this resource+date
        if (form.getHours() != null && form.getHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal already = getLoggedHours(resourceName, date);
            BigDecimal total   = already.add(form.getHours());
            if (total.compareTo(BigDecimal.valueOf(max)) > 0) {
                errors.add(String.format(
                    "TS-01: This task would bring the daily total to %.1f hrs — exceeds the %.0f hr cap. " +
                    "Already logged: %.1f hrs. Remaining: %.1f hrs.",
                    total.doubleValue(), max,
                    already.doubleValue(),
                    BigDecimal.valueOf(max).subtract(already).doubleValue()));
            }
        }

        // TS-06: SOW must be in the list of SOWs the resource is allowed to use
        if (form.getSow() != null && !form.getSow().isBlank()) {
            String chosenSow = form.getSow().trim();
            // Find resourceId
            String resId = resourceRepo.findByName(resourceName)
                .map(r -> r.getResourceId()).orElse(null);
            boolean sowAllowed;
            if (resId != null && resourceSowRepo.existsByResourceIdAndSowNumber(resId, chosenSow)) {
                sowAllowed = true;
            } else if (resId == null) {
                // No resource mapping — fall back to expectedSow check
                sowAllowed = chosenSow.equals(expectedSow);
            } else {
                sowAllowed = false;
            }
            if (!sowAllowed) {
                errors.add("TS-06: SOW '" + chosenSow + "' is not assigned to resource '"
                    + resourceName + "'");
            }
        } else {
            errors.add("TS-06: SOW is required. Please select a SOW from the dropdown.");
        }

        // TS-07: Engagement period
        resourceRepo.findByName(resourceName).ifPresent(res -> {
            if (res.getStartDate() != null && date.isBefore(res.getStartDate()))
                errors.add("TS-07: " + date + " is before your engagement start (" + res.getStartDate() + ")");
            if (res.getEndDate() != null && date.isAfter(res.getEndDate()))
                errors.add("TS-07: " + date + " is after your engagement end (" + res.getEndDate() + ")");
        });

        return errors;
    }

    @Transactional
    public TimesheetEntry save(TimesheetEntryForm form, String submittedBy, String resourceName) {
        LocalDate date = parseDate(form.getEntryDate());
        String task = form.getTask();
        if (form.isWeekendOverride() && form.getWeekendOverrideReason() != null
                && !form.getWeekendOverrideReason().isBlank()) {
            task = (task != null && !task.isBlank() ? task + " | " : "")
                    + "[WEEKEND OVERRIDE: " + form.getWeekendOverrideReason() + "]";
        }
        return entryRepo.save(TimesheetEntry.builder()
                .resourceName(resourceName)
                .entryDate(date)
                .assignedTeam(form.getAssignedTeam())
                .project(form.getProject())
                .subProject(form.getSubProject())
                .projectCode(form.getProjectCode())
                .countryCode(form.getCountryCode() != null ? form.getCountryCode() : "IN")
                .hours(form.getHours())
                .task(task)
                .company(form.getCompany() != null ? form.getCompany() : props.getSow().getClient())
                .sow(form.getSow() != null && !form.getSow().isBlank()
                        ? form.getSow() : props.getValidation().getExpectedSow())
                .submittedBy(submittedBy)
                .status(form.isWeekendOverride() ? "WEEKEND-OVERRIDE" : "SUBMITTED")
                .weekendOverride(form.isWeekendOverride())
                .weekendOverrideReason(form.isWeekendOverride() ? form.getWeekendOverrideReason() : null)
                .build());
    }

    @Transactional
    public void deleteEntry(Long id) {
        entryRepo.deleteById(id);
    }

    public List<TimesheetEntry> getMyEntries(String username) {
        return entryRepo.findBySubmittedByOrderByEntryDateDescSubmittedAtDesc(username);
    }

    public List<TimesheetEntry> getAllEntries() {
        return entryRepo.findAllByOrderByEntryDateDescSubmittedAtDesc();
    }

    /** Returns a map of date → { totalHours, taskCount } for the weekly summary */
    public Map<LocalDate, BigDecimal> getDailySummary(String username) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        entryRepo.dailySummaryByUser(username).forEach(row -> {
            LocalDate d = (LocalDate) row[0];
            BigDecimal h = (BigDecimal) row[1];
            map.put(d, h);
        });
        return map;
    }
}
