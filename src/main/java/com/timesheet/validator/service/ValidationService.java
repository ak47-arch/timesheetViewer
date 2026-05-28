package com.timesheet.validator.service;

import com.timesheet.validator.config.AppProperties;
import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.domain.ValidationIssue;
import com.timesheet.validator.dto.ValidationResultDto;
import com.timesheet.validator.dto.ValidationResultDto.IssueDto;
import com.timesheet.validator.repository.CellDataRepository;
import com.timesheet.validator.repository.PublicHolidayRepository;
import com.timesheet.validator.repository.ResourceRepository;
import com.timesheet.validator.repository.ValidationIssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates the Timesheet sheet of an uploaded workbook against:
 *   TS-01  Max 8 hours per resource per date
 *   TS-02  No weekend entries
 *   TS-03  No public holiday entries
 *   TS-04  Hours must be positive
 *   TS-05  Resource name must exist in roster
 *   TS-06  SOW must match expected value
 *   TS-07  Date must be within billing period for that resource
 *
 * Column mapping (0-based) for sheet "Timesheet":
 *   0=Date  1=Name  2=Team  3=Project  4=SubProject  5=ProjectCode
 *   6=Country  7=Hours  8=Task  9=Company  10=SOW
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private static final String SHEET = "Timesheet";
    // Supported date formats in the Excel file
    private static final List<DateTimeFormatter> DATE_FMTS = List.of(
        DateTimeFormatter.ofPattern("dd-MMM-yy"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final AppProperties props;
    private final CellDataRepository cellRepo;
    private final PublicHolidayRepository holidayRepo;
    private final ResourceRepository resourceRepo;
    private final ValidationIssueRepository issueRepo;

    @Transactional
    public ValidationResultDto validate(String sessionId) {
        issueRepo.deleteBySessionId(sessionId);

        List<CellData> allCells = cellRepo
            .findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(sessionId, SHEET);

        if (allCells.isEmpty()) {
            log.warn("[Validation] No cells found for sheet '{}' session={}", SHEET, sessionId);
            return ValidationResultDto.builder()
                .sessionId(sessionId).passed(true)
                .errors(List.of()).warnings(List.of()).build();
        }

        // Build a row-map: rowIdx → (colIdx → CellData)
        TreeMap<Integer, Map<Integer, CellData>> rowMap = new TreeMap<>();
        for (CellData c : allCells) {
            rowMap.computeIfAbsent(c.getRowIdx(), k -> new TreeMap<>()).put(c.getColIdx(), c);
        }

        int headerRow = rowMap.firstKey();
        Set<String> knownNames = resourceRepo.findAll().stream()
            .map(r -> r.getName().trim().toLowerCase())
            .collect(Collectors.toSet());
        Set<LocalDate> holidays = holidayRepo.findAll().stream()
            .map(h -> h.getHolidayDate())
            .collect(Collectors.toSet());
        String expectedSow = props.getValidation().getExpectedSow();
        double maxHours = props.getValidation().getMaxHoursPerDay();

        List<ValidationIssue> issues = new ArrayList<>();

        // ── Aggregate daily hours per (name, date) for TS-01 ─────────────────
        Map<String, Map<LocalDate, Double>> dailyHours = new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, CellData>> rowEntry : rowMap.entrySet()) {
            int ri = rowEntry.getKey();
            if (ri == headerRow) continue; // skip header
            Map<Integer, CellData> cols = rowEntry.getValue();

            String rawDate  = val(cols, 0);
            String name     = val(cols, 1);
            String hoursStr = val(cols, 7);
            String sow      = val(cols, 10);
            String country  = val(cols, 6);

            if (rawDate.isBlank() && name.isBlank()) continue; // blank row

            LocalDate date = parseDate(rawDate);

            // TS-02: Weekend
            if (date != null && !props.getValidation().isAllowWeekendOverride()) {
                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    issues.add(issue(sessionId,"TS-02","CRITICAL",ri,0,"Date",
                        "Weekend entry not allowed: " + rawDate + " (" + dow + ") for resource '" + name + "'"));
                }
            }

            // TS-03: Public holiday
            if (date != null && holidays.contains(date)) {
                String hName = holidayRepo.findAll().stream()
                    .filter(h -> h.getHolidayDate().equals(date))
                    .map(h -> h.getHolidayName()).findFirst().orElse("holiday");
                issues.add(issue(sessionId,"TS-03","CRITICAL",ri,0,"Date",
                    "Entry on public holiday '" + hName + "': " + rawDate + " for resource '" + name + "'"));
            }

            // TS-04: Hours positive
            double hours = 0;
            try {
                hours = Double.parseDouble(hoursStr.trim());
                if (hours <= 0) {
                    issues.add(issue(sessionId,"TS-04","CRITICAL",ri,7,"Hours",
                        "Hours must be positive, got: " + hoursStr + " for resource '" + name + "'"));
                }
            } catch (NumberFormatException e) {
                if (!hoursStr.isBlank()) {
                    issues.add(issue(sessionId,"TS-04","CRITICAL",ri,7,"Hours",
                        "Invalid hours value: '" + hoursStr + "' for resource '" + name + "'"));
                }
            }

            // TS-05: Known resource
            if (!name.isBlank() && !knownNames.contains(name.trim().toLowerCase())) {
                issues.add(issue(sessionId,"TS-05","WARNING",ri,1,"Name",
                    "Resource '" + name + "' not found in roster"));
            }

            // TS-06: SOW match
            if (!sow.isBlank() && !sow.trim().equals(expectedSow)) {
                issues.add(issue(sessionId,"TS-06","CRITICAL",ri,10,"SOW",
                    "SOW mismatch: found '" + sow + "', expected '" + expectedSow + "'"));
            }

            // TS-07: Date within resource engagement period
            if (date != null && !name.isBlank()) {
                resourceRepo.findByName(name.trim()).ifPresent(res -> {
                    if (res.getStartDate() != null && date.isBefore(res.getStartDate())) {
                        issues.add(issue(sessionId,"TS-07","WARNING",ri,0,"Date",
                            "Date " + date + " is before engagement start (" + res.getStartDate() + ") for '" + name + "'"));
                    }
                    if (res.getEndDate() != null && date.isAfter(res.getEndDate())) {
                        issues.add(issue(sessionId,"TS-07","WARNING",ri,0,"Date",
                            "Date " + date + " is after engagement end (" + res.getEndDate() + ") for '" + name + "'"));
                    }
                });
            }

            // Accumulate for TS-01
            if (date != null && !name.isBlank() && hours > 0) {
                dailyHours
                    .computeIfAbsent(name.trim().toLowerCase(), k -> new HashMap<>())
                    .merge(date, hours, Double::sum);
            }
        }

        // TS-01: Max hours per day — checked after full scan
        dailyHours.forEach((name, dateMap) -> dateMap.forEach((date, total) -> {
            if (total > maxHours) {
                issues.add(issue(sessionId,"TS-01","CRITICAL",-1,7,"Hours",
                    String.format("Resource '%s' logged %.1f hrs on %s (max %.0f hrs/day)", name, total, date, maxHours)));
            }
        }));

        issueRepo.saveAll(issues);
        log.info("[Validation] session={} issues={}", sessionId, issues.size());

        List<IssueDto> errors = toDto(issues.stream()
            .filter(i -> "CRITICAL".equals(i.getSeverity())).collect(Collectors.toList()));
        List<IssueDto> warnings = toDto(issues.stream()
            .filter(i -> "WARNING".equals(i.getSeverity())).collect(Collectors.toList()));

        return ValidationResultDto.builder()
            .sessionId(sessionId)
            .passed(errors.isEmpty())
            .errorCount(errors.size())
            .warningCount(warnings.size())
            .errors(errors)
            .warnings(warnings)
            .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String val(Map<Integer, CellData> cols, int col) {
        CellData c = cols.get(col);
        return (c == null || c.getDisplayValue() == null) ? "" : c.getDisplayValue().trim();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FMTS) {
            try { return LocalDate.parse(s.trim(), fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private ValidationIssue issue(String sid, String ruleId, String severity,
                                  int row, int col, String field, String msg) {
        return ValidationIssue.builder()
            .sessionId(sid).ruleId(ruleId).severity(severity)
            .sheetName(SHEET).rowIdx(row).colIdx(col)
            .fieldName(field).message(msg).build();
    }

    private List<IssueDto> toDto(List<ValidationIssue> list) {
        return list.stream().map(i -> IssueDto.builder()
                .ruleId(i.getRuleId()).severity(i.getSeverity())
                .sheetName(i.getSheetName()).rowIdx(i.getRowIdx()).colIdx(i.getColIdx())
                .fieldName(i.getFieldName()).message(i.getMessage()).build())
            .collect(Collectors.toList());
    }

    private String valRaw(Map<Integer, CellData> cols, int col) {
        CellData c = cols.get(col);
        if (c == null) return "";
        String raw = c.getRawValue();
        if (raw != null && !raw.isBlank()) return raw.trim();
        return c.getDisplayValue() == null ? "" : c.getDisplayValue().trim();
    }
}
