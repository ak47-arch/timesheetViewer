package com.timesheet.validator.service;

import com.timesheet.validator.config.RuleCatalog;
import com.timesheet.validator.domain.UploadSession;
import com.timesheet.validator.repository.UploadSessionRepository;
import com.timesheet.validator.config.AppProperties;
import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.domain.ValidationIssue;
import com.timesheet.validator.dto.ValidationResultDto;
import com.timesheet.validator.dto.ValidationResultDto.IssueDto;
import com.timesheet.validator.model.CellReference;
import com.timesheet.validator.model.ProjectCodeKey;
import com.timesheet.validator.model.ProjectKey;
import com.timesheet.validator.model.ProjectSummary;
import com.timesheet.validator.model.ProjectWiseHierarchy;
import com.timesheet.validator.model.SubProjectKey;
import com.timesheet.validator.model.SubProjectSummary;
import com.timesheet.validator.service.ProjectWiseParser;
import com.timesheet.validator.model.ProjectCodeSummary;
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
    private static final String PIVOT_SHEET = "Pivot";
//    private static final double HOURS_PER_DAY = 8.0;
    private static final String PROJECT_WISE_SHEET = "Projectwise";
    
    private final ProjectWiseParser projectWiseParser;

    //additon of new code
    private Map<String, Integer> pivotEmployeeRows =
            new HashMap<>();
    private Map<String, Integer> pivotDateColumns =
            new HashMap<>();

    // Supported date formats in the Excel file
    private static final List<DateTimeFormatter> DATE_FMTS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),                    // ISO — rawValue (primary)
        DateTimeFormatter.ofPattern("dd-MMM-yy",   Locale.ENGLISH),  // 01-Mar-26
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),  // 01-Mar-2026
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),                    // 01/03/2026
        DateTimeFormatter.ofPattern("M/d/yy"),                        // 3/1/26
        DateTimeFormatter.ofPattern("dd-MM-yyyy")                     // 01-03-2026
    );

    private final AppProperties props;
    private final CellDataRepository cellRepo;
    private final PublicHolidayRepository holidayRepo;
    private final ResourceRepository resourceRepo;
    private final ValidationIssueRepository issueRepo;
    private final UploadSessionRepository sessionRepo;
    private final RuleCatalog ruleCatalog;

    @Transactional
    public ValidationResultDto validate(String sessionId) {

        log.info("VALIDATION STARTED FOR SESSION {}", sessionId);

        issueRepo.deleteBySessionId(sessionId);

        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        Set<String> enabledRules = new HashSet<>();

        if (session.getEnabledRules() != null &&
                !session.getEnabledRules().isBlank()) {

            enabledRules.addAll(
                    Arrays.stream(session.getEnabledRules().split(","))
                            .map(String::trim)
                            .collect(Collectors.toSet())
            );
        }

//        log.info("ENABLED RULES = {}", enabledRules);

        List<CellData> allCells = cellRepo
            .findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(sessionId, SHEET);


//        log.info("=========== TIMESHEET HEADER DEBUG ===========");
//
//        for (CellData cell : allCells) {
//
//            if (cell.getRowIdx() == 0) {
//
//                log.info(
//                        "TIMESHEET HEADER col={} value={}",
//                        cell.getColIdx(),
//                        cell.getDisplayValue()
//                );
//            }
//        }
//
//        log.info("==============================================");


        List<CellData> pivotCells =
                cellRepo.findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(
                        sessionId,
                        PIVOT_SHEET
                );


        List<CellData> projectWiseCells =
        cellRepo.findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(
                sessionId,
                PROJECT_WISE_SHEET);        


//        log.info("=========== PIVOT  ROW = 3 DEBUG ===========");
//
//        for (CellData cell : pivotCells) {
//
//            if (cell.getRowIdx() == 3) {
//
//                log.info(
//                        "PIVOT HEADER col={} value={}",
//                        cell.getColIdx(),
//                        cell.getDisplayValue()
//                );
//            }
//        }
//
//        log.info("===========================================");


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
            .filter(h -> h.isActive())
            .map(h -> h.getHolidayDate())
            .collect(Collectors.toSet());
        String expectedSow = props.getValidation().getExpectedSow();
        double maxHours = props.getValidation().getMaxHoursPerDay();

        List<ValidationIssue> issues = new ArrayList<>();


        // Phase gate: pivot reconciliation runs only once the Timesheet phase
        // has passed. In TIMESHEET phase we emit Timesheet (TS-xx) issues only.
        boolean pivotPhase = "PIVOT".equalsIgnoreCase(session.getValidationPhase());

        boolean projectWisePhase =
        "PROJECT_WISE".equalsIgnoreCase(
                session.getValidationPhase());

        if (!pivotCells.isEmpty() && pivotPhase) {

            int pivotGtRow = pivotGrandTotalRow(pivotCells, 21);

            Set<String> timesheetEmployees =
                    extractTimesheetEmployees(allCells);

            Set<String> pivotEmployees =
                    extractPivotEmployees(pivotCells);

            Set<String> missingPivotEmployees =
                    new HashSet<>(timesheetEmployees);

            missingPivotEmployees.removeAll(pivotEmployees);

            Map<String, Double> timesheetTotals =
                    extractTimesheetEmployeeTotals(allCells);

            Map<String, Double> pivotTotals =
                    extractPivotEmployeeTotals(pivotCells);

            log.info("TIMESHEET TOTALS = {}", timesheetTotals);
            log.info("PIVOT TOTALS = {}", pivotTotals);


            Map<String, Double> timesheetDateTotals =
                    extractTimesheetDateTotals(allCells);

            Map<String, Double> pivotDateTotals =
                    extractPivotDateTotals(pivotCells);

            log.info("TIMESHEET DATE TOTALS={}",
                    timesheetDateTotals);

            log.info("PIVOT DATE TOTALS={}",
                    pivotDateTotals);


//            log.info("TIMESHEET EMPLOYEES = {}", timesheetEmployees);
//            log.info("PIVOT EMPLOYEES = {}", pivotEmployees);


            PivotLayout layout = findPivotLayout(pivotCells);

            int grandTotalColumn = layout.getGrandTotalColumn();
            int workingDaysColumn = grandTotalColumn + 1;


            // =========================
            // PS-01 Resource Validation
            // =========================

            for (String employee
                    : timesheetEmployees) {

                if (!pivotEmployees.contains(employee)) {

                    issues.add(
                            pivotIssue(
                                    sessionId,
                                    "PS-01",
                                    "CRITICAL",
                                    -1,
                                    0,
                                    "Employee Name",
                                    "Resource '" +employee+  "' missing in Pivot sheet. " +
                                            "Please verify employee list for the project. "
                            )
                    );
                }
            }


            // =========================
            // PS-02 Hours Validation
            // =========================

            Map<String, Integer> pivotEmployeeRows =
                    extractPivotEmployeeRows(pivotCells);



            for (Map.Entry<String, Double> entry
                    : timesheetTotals.entrySet()) {

                String employee =
                        entry.getKey();

                Double timesheetHours =
                        entry.getValue();

                Double pivotHours =
                        pivotTotals.get(employee);

                if (pivotHours == null) {
                    continue;
                }

                if (Math.abs(timesheetHours - pivotHours) > 0.01) {

                    issues.add(
                            pivotIssue(
                                    sessionId,
                                    "PS-02",
                                    "CRITICAL",
                                    pivotEmployeeRows.getOrDefault(employee, -1),
                                    grandTotalColumn, // Grand Total column
                                    "Grand Total",
                                    String.format(
                                            "Total Hours Calculation wrong for %s. Please Check Entries. Timesheet=%.1f Pivot=%.1f",
                                            employee,
                                            timesheetHours,
                                            pivotHours
                                    )
                            )
                    );
                }
            }

            // =========================
            // PS-03 Date wise hours calculation validation
            // =========================

            Map<String, Integer> pivotDateColumns =
                    extractPivotDateColumns(pivotCells);

            for (Map.Entry<String, Double> entry
                    : timesheetDateTotals.entrySet()) {

                String date = entry.getKey();

                Double timesheetHours =
                        entry.getValue();

                Double pivotHours =
                        pivotDateTotals.get(date);

                if (pivotHours == null) {
                    continue;
                }

                if (Math.abs(timesheetHours - pivotHours) > 0.01) {

//                    issues.add(
//                            pivotIssue(
//                                    sessionId,
//                                    "PS-03",
//                                    "CRITICAL",
//                                    "Date-wise Total",
//                                    String.format(
//                                            "%s Total Hours Calculation wrong. Timesheet=%.1f Pivot=%.1f",
//                                            date,
//                                            timesheetHours,
//                                            pivotHours
//                                    )
//                            )
//                    );


                    Integer col = pivotDateColumns.get(date);

                    if (col != null) {

                        issues.add(
                                pivotIssue(
                                        sessionId,
                                        "PS-03",
                                        "CRITICAL",
                                        pivotGtRow,
                                        col,
                                        "Date-wise Total",
                                        String.format(
                                                "%s Total Hours Calculation wrong. Timesheet=%.1f Pivot=%.1f",
                                                date,
                                                timesheetHours,
                                                pivotHours
                                        )
                                )
                        );
                    }

                }
            }


            //PS-04



            Map<String, Double> timesheetEmployeeDateTotals =
                    extractTimesheetEmployeeDateTotals(allCells);

            Map<String, Double> pivotEmployeeDateTotals =
                    extractPivotEmployeeDateTotals(pivotCells);

//            log.info(
//                    "TIMESHEET EMPLOYEE DATE TOTALS={}",
//                    timesheetEmployeeDateTotals);
//
//            log.info(
//                    "PIVOT EMPLOYEE DATE TOTALS={}",
//                    pivotEmployeeDateTotals);

            for (Map.Entry<String, Double> entry
                    : timesheetEmployeeDateTotals.entrySet()) {

                String employeeDate =
                        entry.getKey();

                Double timesheetHours =
                        entry.getValue();

                Double pivotHours =
                        pivotEmployeeDateTotals.get(employeeDate);

                if (pivotHours == null) {
                    pivotHours = 0.0;
                }

                if (Math.abs(timesheetHours - pivotHours) > 0.01) {

                    String[] parts =
                            employeeDate.split("\\|");

                    String employee =
                            parts[0];

                    if (missingPivotEmployees.contains(employee)) {
                        continue;
                    }

                    String date =
                            parts[1];

//                    issues.add(
//                            pivotIssue(
//                                    sessionId,
//                                    "PS-04",
//                                    "CRITICAL",
//                                    pivotEmployeeRows.getOrDefault(employee, -1),
//                                    -1,
//                                    "Employee-Date Validation",
//                                    String.format(
//                                            "%s has incorrect hours on %s. Timesheet=%.1f Pivot=%.1f",
//                                            employee,
//                                            date,
//                                            timesheetHours,
//                                            pivotHours
//                                    )
//                            )
//                    );



                    Integer row =
                            pivotEmployeeRows.getOrDefault(
                                    employee,
                                    -1
                            );

                    Integer col =
                            pivotDateColumns.getOrDefault(
                                    date,
                                    -1
                            );

//                    System.out.println("######LLLLoggggss#######");
//                    log.info(
//                            "PS04 -> employee={} row={} date={} col={}",
//                            employee,
//                            row,
//                            date,
//                            col
//                    );

//                    log.info(
//                            "PS04 ISSUE -> row={} col={} employee={} date={}",
//                            row,
//                            col,
//                            employee,
//                            date
//                    );



                    String validationMessage =
                            String.format(
                                    "%s has incorrect hours on %s. Timesheet=%.1f Pivot=%.1f",
                                    employee,
                                    date,
                                    timesheetHours,
                                    pivotHours
                            );


                    issues.add(
                            pivotIssue(
                                    sessionId,
                                    "PS-04",
                                    "CRITICAL",
                                    row,
                                    col,
                                    "Employee-Date Validation",
                                    validationMessage
                            )
                    );

                    if (row >= 0) {

                        issues.add(
                                pivotIssue(
                                        sessionId,
                                        "PS-04",
                                        "CRITICAL",
                                        row,
                                        0,
                                        "Employee",
                                        validationMessage
                                )
                        );
                    }

                }
            }

            //PS-05
            Double pivotGrandTotal = extractPivotGrandTotal(pivotCells);

            Double calculatedTotal = calculatePivotDateColumnTotal(pivotCells);


//            log.info(
//                    "FR3 CHECK -> Calculated={} Pivot={}",
//                    calculatedTotal,
//                    pivotGrandTotal);


            if (pivotGrandTotal != null
                    && Math.abs(
                    pivotGrandTotal
                            - calculatedTotal) > 0.01) {

                issues.add(

                        pivotIssue(
                                sessionId,
                                "PS-05",
                                "CRITICAL",
                                pivotGtRow,
                                grandTotalColumn,
                                "Pivot Grand Total",
                                String.format(
                                        "Pivot Grand Total calculation incorrect. Expected=%.1f Actual=%.1f",
                                        calculatedTotal,
                                        pivotGrandTotal
                                )
                        )

//                        pivotIssue(
//                                sessionId,
//                                "PS-05",
//                                "CRITICAL",
//                                "Pivot Grand Total",
//                                String.format(
//                                        "Pivot Grand Total calculation incorrect. Expected=%.1f Actual=%.1f",
//                                        calculatedTotal,
//                                        pivotGrandTotal
//                                )
//                        )
                );
            }


            //PS-06
            Map<String, Double> pivotDays =
                    extractPivotEmployeeDays(pivotCells);

            log.info("PIVOT DAYS = {}", pivotDays);


            for (Map.Entry<String, Double> entry
                    : pivotTotals.entrySet()) {

                String employee =
                        entry.getKey();

                Double totalHours =
                        entry.getValue();

                Double actualDays =
                        pivotDays.get(employee);

                if (actualDays == null) {
                    continue;
                }

                double workingHoursPerDay =
                        getWorkingHoursPerDay(employee);

                double expectedDays =
                        totalHours / workingHoursPerDay;


                log.info(
                        "PS-06 -> employee={} totalHours={} workingHoursPerDay={} expectedDays={} actualDays={}",
                        employee,
                        totalHours,
                        workingHoursPerDay,
                        expectedDays,
                        actualDays
                );


                if (Math.abs(
                        expectedDays - actualDays) > 0.01) {


                    int row = pivotEmployeeRows.getOrDefault(employee, -1);

                    String message =
                            String.format(
                                    "Working days calculation mismatch with pivot excel. Please review calculations. Employee: %s Expected: %.1f Actual: %.1f",
                                    employee,
                                    expectedDays,
                                    actualDays
                            );

// Employee name cell
                    issues.add(
                            pivotIssue(
                                    sessionId,
                                    "PS-06",
                                    "CRITICAL",
                                    row,
                                    0,
                                    "Working Days",
                                    message

                            )
                    );

// Working Days cell
                    issues.add(
                            pivotIssue(
                                    sessionId,
                                    "PS-06",
                                    "CRITICAL",
                                    row,
                                    workingDaysColumn,
                                    "Working Days",
                                    message
                            )
                    );

                }
            }


        }




        // ── Aggregate daily hours per (name, date) for TS-01 ─────────────────

        // ======================================================
        // PROJECT WISE VALIDATION
        // ======================================================

        if (projectWisePhase && !projectWiseCells.isEmpty()) {

        validateProjectWise(
                sessionId,
                allCells,
                projectWiseCells,
                issues);
        }
        Map<String, Map<LocalDate, Double>> dailyHours = new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, CellData>> rowEntry : rowMap.entrySet()) {
            int ri = rowEntry.getKey();
            if (ri == headerRow) continue; // skip header
            Map<Integer, CellData> cols = rowEntry.getValue();

//            String rawDate  = valRaw(cols, 0); // rawValue = ISO yyyy-MM-dd, reliable for parseDate()
//            String name     = val(cols, 1);
//            String hoursStr = val(cols, 7);
//            String sow      = val(cols, 10);
//            String country  = val(cols, 6);


//            String rawDate  =
            String rawDate = valRaw(cols, 0);
            String name     = val(cols, 1);
            String assignedTeam     = val(cols, 2);
            String project  = val(cols, 3);
            String subProject = val(cols, 4);
            String projectCode = val(cols, 5);
            String country  = val(cols, 6);
            String hoursStr = val(cols, 7);
            String task     = val(cols, 8);
            String company  = val(cols, 9);
            String sow      = val(cols, 10);


            if (rawDate.isBlank() && name.isBlank()) continue; // blank row


            String[] fieldNames = {
                    "Date",
                    "Name",
                    "Assigned Team",
                    "Project",
                    "Sub Project",
                    "Project Code",
                    "Country",
                    "Hours",
                    "Task",
                    "Company",
                    "SOW"
            };

            if (isRuleEnabled(enabledRules, "TS-08")) {

                for (int col = 0; col < fieldNames.length; col++) {

                    String value = val(cols, col);

                    if (value.isBlank()) {

                        issues.add(issue(
                                sessionId,
                                "TS-08",
                                "CRITICAL",
                                ri,
                                col,
                                fieldNames[col],
                                fieldNames[col] +
                                        " is mandatory and cannot be blank for resource '" +
                                        name + "'"
                        ));
                    }
                }
            }



            LocalDate date = parseDate(rawDate);

//            System.out.println(
//                    "Row=" + ri +
//                            " RawDate=" + rawDate +
//                            " ParsedDate=" + date
//            );

            // TS-02: Weekend
            if (isRuleEnabled(enabledRules, "TS-02")
                    && date != null
                    && !props.getValidation().isAllowWeekendOverride()) {

//                System.out.println(
//                        "Checking weekend for " +
//                                date +
//                                " Day=" +
//                                date.getDayOfWeek()
//                );

                DayOfWeek dow = date.getDayOfWeek();



                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {

//                    System.out.println("WEEKEND FOUND");

                    issues.add(issue(sessionId,"TS-02","CRITICAL",ri,0,"Date",
                        "Weekend entry not allowed: " + rawDate + " (" + dow + ") for resource '" + name + "'"));
                }
            }

            // TS-03: Public holiday
            if (isRuleEnabled(enabledRules, "TS-03")
                    && date != null
                    && holidays.contains(date)) {
                String hName = holidayRepo.findAll().stream()
                    .filter(h -> h.getHolidayDate().equals(date))
                    .map(h -> h.getHolidayName()).findFirst().orElse("holiday");
                issues.add(issue(sessionId,"TS-03","CRITICAL",ri,0,"Date",
                    "Entry on public holiday '" + hName + "': " + rawDate + " for resource '" + name + "'"));
            }

            // TS-04: Hours positive
            double hours = 0;

            if (!hoursStr.isBlank()) {

                try {
                    hours = Double.parseDouble(hoursStr.trim());

                    if (isRuleEnabled(enabledRules, "TS-04")
                            && hours <= 0) {

                        issues.add(issue(
                                sessionId,
                                "TS-04",
                                "CRITICAL",
                                ri,
                                7,
                                "Hours",
                                "Hours must be positive, got: " + hoursStr +
                                        " for resource '" + name + "'"
                        ));
                    }

                } catch (NumberFormatException e) {

                    if (isRuleEnabled(enabledRules, "TS-04")) {

                        issues.add(issue(
                                sessionId,
                                "TS-04",
                                "CRITICAL",
                                ri,
                                7,
                                "Hours",
                                "Invalid hours value: '" + hoursStr + "'"
                        ));
                    }
                }
            }

            // TS-05: Known resource
            if (isRuleEnabled(enabledRules, "TS-05")
                    && !name.isBlank()
                    && !knownNames.contains(name.trim().toLowerCase())) {
                issues.add(issue(sessionId,"TS-05","WARNING",ri,1,"Name",
                    "Resource '" + name + "' not found in roster"));
            }

            // TS-06: SOW match
            if (isRuleEnabled(enabledRules, "TS-06")
                    && !sow.isBlank()
                    && !sow.trim().equals(expectedSow)) {
                issues.add(issue(sessionId,"TS-06","CRITICAL",ri,10,"SOW",
                    "SOW mismatch: found '" + sow + "', expected '" + expectedSow + "'" + " for resource '" + name + "'"));
            }

            // TS-07: Date within resource engagement period
            if (isRuleEnabled(enabledRules, "TS-07")
                    && date != null
                    && !name.isBlank()) {
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
        if (isRuleEnabled(enabledRules, "TS-01")) {
            dailyHours.forEach((name, dateMap) ->
                    dateMap.forEach((date, total) -> {
                                if (total != maxHours) {
                                    issues.add(issue(sessionId, "TS-01", "CRITICAL", -1, 7, "Hours",
                                            String.format("Resource '%s' logged %.1f hrs on %s (max %.0f hrs/day)", name, total, date, maxHours)));
                                }
                            })
            );
        };
//        }));

        // Global enable/disable gate (DB-driven): drop any issue whose rule is
        // turned off in RULE_CONFIG. Covers both Timesheet (TS-xx) and Pivot
        // (PS-xx) rules without touching individual emission sites. Rules not
        // managed by the catalog are left untouched.
        issues.removeIf(i -> i.getRuleId() != null
                && !ruleCatalog.isGloballyEnabled(i.getRuleId()));

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

    /**
     * Applies the (optional) admin-configured message template for a rule.
     * Supported placeholders: {detail} (the engine-computed message),
     * {ruleId}, {severity}, {field}. If no template is configured the
     * engine-computed message is returned unchanged.
     */
    private String renderMessage(String ruleId, String severity, String field, String detail) {
        String tpl = ruleCatalog.getMessageTemplate(ruleId);
        if (tpl == null || tpl.isBlank()) return detail;
        return tpl
                .replace("{detail}", detail == null ? "" : detail)
                .replace("{ruleId}", ruleId == null ? "" : ruleId)
                .replace("{severity}", severity == null ? "" : severity)
                .replace("{field}", field == null ? "" : field);
    }

    private ValidationIssue issue(String sid, String ruleId, String severity,
                                  int row, int col, String field, String msg) {
        return ValidationIssue.builder()
            .sessionId(sid).ruleId(ruleId).severity(severity)
            .sheetName(SHEET).rowIdx(row).colIdx(col)
            .fieldName(field).message(renderMessage(ruleId, severity, field, msg)).build();
    }

    private ValidationIssue pivotIssue(
            String sid,
            String ruleId,
            String severity,
            int row,
            int col,
            String field,
            String msg) {

        return ValidationIssue.builder()
                .sessionId(sid)
                .ruleId(ruleId)
                .severity(severity)
                .sheetName("Pivot")
                .rowIdx(row)
                .colIdx(col)
                .fieldName(field)
                .message(renderMessage(ruleId, severity, field, msg))
                .build();
    }

    private ValidationIssue pivotIssue(
            String sid,
            String ruleId,
            String severity,
            String field,
            String msg
            ) {

        return ValidationIssue.builder()
                .sessionId(sid)
                .ruleId(ruleId)
                .severity(severity)
                .sheetName("Pivot")
                .fieldName(field)
                .message(renderMessage(ruleId, severity, field, msg))
                .build();
    }


    private List<IssueDto> toDto(List<ValidationIssue> list) {
        return list.stream().map(i -> IssueDto.builder()
                .ruleId(i.getRuleId()).severity(i.getSeverity())
                .sheetName(i.getSheetName()).rowIdx(i.getRowIdx()).colIdx(i.getColIdx())
                .fieldName(i.getFieldName()).message(i.getMessage()).build())
            .collect(Collectors.toList());
    }


    private boolean isRuleEnabled(Set<String> enabledRules,
                                  String ruleId) {

        return enabledRules.contains(ruleId);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(
                    value == null ? "0" : value.trim()
            );
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeName(String name) {

        if (name == null) {
            return "";
        }

        return name.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private double getWorkingHoursPerDay(String employeeName) {

        String normalizedEmployee = normalizeName(employeeName);

        return props.getResources()
                .stream()
                .filter(r ->
                        normalizeName(r.getName())
                                .equals(normalizedEmployee))
                .map(AppProperties.ResourceProps::getWorkingHoursPerDay)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(8.0);
    }


    private Set<String> extractTimesheetEmployees(
            List<CellData> timesheetCells) {

        Set<String> employees = new HashSet<>();

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : timesheetCells) {

            rowMap.computeIfAbsent(
                    c.getRowIdx(),
                    k -> new TreeMap<>()
            ).put(c.getColIdx(), c);
        }

        int headerRow = rowMap.firstKey();

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

            if (entry.getKey() == headerRow) {
                continue;
            }

            CellData nameCell =
                    entry.getValue().get(1);

            if (nameCell != null &&
                    nameCell.getDisplayValue() != null &&
                    !nameCell.getDisplayValue().isBlank()) {

                employees.add(
                        nameCell.getDisplayValue()
                                .trim()
                                .toLowerCase()
                );
            }
        }

        return employees;
    }


    private Set<String> extractPivotEmployees(
            List<CellData> pivotCells) {

        Set<String> employees = new HashSet<>();

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : pivotCells) {

            rowMap.computeIfAbsent(
                    c.getRowIdx(),
                    k -> new TreeMap<>()
            ).put(c.getColIdx(), c);
        }



        boolean employeeSectionStarted = false;

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

            Map<Integer, CellData> cols =
                    entry.getValue();

            CellData firstColumn =
                    cols.get(0);

            if (firstColumn == null) {
                continue;
            }

            String value =
                    firstColumn.getDisplayValue();

            if (value == null || value.isBlank()) {
                continue;
            }

            value = value.trim();

            log.info(
                    "ROW={} VALUE={}",
                    entry.getKey(),
                    value
            );

             //Start reading employees after Row Labels
//            if ("Row Lables".equalsIgnoreCase(value)) {
//
//                employeeSectionStarted = true;
//                continue;
//            }

            log.info(
                    "FIRST COLUMN VALUE='{}'",
                    value
            );

            if (isRowLabelHeader(value)) {

                employeeSectionStarted = true;
                continue;
            }




            if (!employeeSectionStarted) {
                continue;
            }

            // Stop when Grand Total reached
            if (value.toLowerCase().contains("grand total")) {
                break;
            }

            employees.add(value.toLowerCase());
        }

        return employees;
    }


    private Map<String, Double> extractTimesheetEmployeeTotals(
            List<CellData> timesheetCells) {

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : timesheetCells) {

            rowMap.computeIfAbsent(
                    c.getRowIdx(),
                    k -> new TreeMap<>()
            ).put(c.getColIdx(), c);
        }



        int headerRow = rowMap.firstKey();

        Map<String, Double> totals =
                new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

            if (entry.getKey() == headerRow) {
                continue;
            }

            Map<Integer, CellData> cols =
                    entry.getValue();

            String employee =
                    val(cols, 1);

            String hoursStr =
                    val(cols, 7);

            if (employee.isBlank()
                    || hoursStr.isBlank()) {
                continue;
            }




            try {

                double hours =
                        Double.parseDouble(hoursStr);

                totals.merge(
                        employee.trim().toLowerCase(),
                        hours,
                        Double::sum
                );

            } catch (Exception ignored) {
            }
        }

        return totals;
    }


    private Integer findPivotGrandTotalColumn(List<CellData> pivotCells) {

        for (CellData cell : pivotCells) {

            if ("Grand Total".equalsIgnoreCase(cell.getDisplayValue())) {
                return cell.getColIdx();
            }

        }

        return null;
    }

    private Map<String, Double> extractPivotEmployeeTotals(
            List<CellData> pivotCells) {

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : pivotCells) {

            rowMap.computeIfAbsent(
                    c.getRowIdx(),
                    k -> new TreeMap<>()
            ).put(c.getColIdx(), c);
        }

//        Integer headerRow = null;
//        Integer grandTotalColumn = null;
//
//        for (Map.Entry<Integer, Map<Integer, CellData>> entry
//                : rowMap.entrySet()) {
//
//            for (CellData cell :
//                    entry.getValue().values()) {
//
//                if ("Grand Total".equalsIgnoreCase(
//                        cell.getDisplayValue())) {
//
//                    headerRow = entry.getKey();
//
//                    grandTotalColumn = cell.getColIdx();
//
//                    break;
//                }
//            }
//
//            if (grandTotalColumn != null) {
//                break;
//            }
//        }


//        Integer grandTotalColumn = findPivotGrandTotalColumn(pivotCells);

//        Integer headerRow = null;

//        if (grandTotalColumn != null) {
//
//            for (Map.Entry<Integer, Map<Integer, CellData>> entry : rowMap.entrySet()) {
//
//                if (entry.getValue().containsKey(grandTotalColumn)
//                        && "Grand Total".equalsIgnoreCase(
//                        entry.getValue().get(grandTotalColumn).getDisplayValue())) {
//
//                    headerRow = entry.getKey();
//                    break;
//                }
//            }
//        }


        PivotLayout layout = findPivotLayout(pivotCells);

        if (layout == null) {
            return new HashMap<>();
        }

        Integer headerRow = layout.getHeaderRow();
        Integer grandTotalColumn = layout.getGrandTotalColumn();


        Map<String, Double> totals =
                new HashMap<>();

        if (headerRow == null || grandTotalColumn == null) {

            return totals;
        }

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

            if (entry.getKey() <= headerRow) {
                continue;
            }

            Map<Integer, CellData> cols = entry.getValue();

            String employee = val(cols, 0);

            if (employee.isBlank()) {
                continue;
            }

            if (employee.toLowerCase()
                    .contains("grand total")) {

                break;
            }

            String totalStr = val(cols, grandTotalColumn);

            if (totalStr.isBlank()) {
                continue;
            }

            try {

                totals.put(
                        employee.trim().toLowerCase(),
                        Double.parseDouble(totalStr)
                );

            } catch (Exception ignored) {
            }
        }

        return totals;
    }



    private Map<String, Double> extractTimesheetDateTotals(
            List<CellData> allCells) {

        Map<String, Double> totals = new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                allCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a, b) -> a)));

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rows.entrySet()) {

            // Skip header row
            if (entry.getKey() == 0) {
                continue;
            }

            Map<Integer, CellData> row = entry.getValue();

            CellData dateCell = row.get(0);
            CellData hoursCell = row.get(7);

            if (dateCell == null || hoursCell == null) {
                continue;
            }

            String date =
                    safe(dateCell.getRawValue());

            if (date.isEmpty()) {
                continue;
            }

            double hours =
                    parseDouble(hoursCell.getRawValue());

            totals.merge(
                    date,
                    hours,
                    Double::sum
            );
        }

        return totals;
    }


    /**
     * Locates the Pivot "Grand Total" row dynamically instead of assuming a
     * fixed index. The column header "Grand Total" sits near the top (row 3),
     * while the row label "Grand Total" sits at the bottom — so we take the
     * bottom-most match. Falls back to {@code fallback} when no label is found,
     * preserving previous behaviour for layouts that lack the label.
     */
    private int pivotGrandTotalRow(List<CellData> pivotCells, int fallback) {
        return pivotCells.stream()
                .filter(c -> c.getRowIdx() != null)
                .filter(c -> "grand total".equalsIgnoreCase(safe(c.getDisplayValue()).trim())
                          || "grand total".equalsIgnoreCase(safe(c.getRawValue()).trim()))
                .map(CellData::getRowIdx)
                .max(Integer::compareTo)
                .orElse(fallback);
    }

    private Map<String, Double> extractPivotDateTotals(
            List<CellData> pivotCells) {

        Map<String, Double> totals = new HashMap<>();

//        Map<Integer, CellData> headerRow =
//                pivotCells.stream()
//                        .filter(c -> c.getRowIdx() == 3)
//                        .collect(Collectors.toMap(
//                                CellData::getColIdx,
//                                c -> c));


        PivotLayout layout = findPivotLayout(pivotCells);

        if (layout == null) {
            return totals;
        }

        int headerRowIndex = layout.getHeaderRow();

        Map<Integer, CellData> headerRow =
                pivotCells.stream()
                        .filter(c -> c.getRowIdx() == headerRowIndex)
                        .collect(Collectors.toMap(
                                CellData::getColIdx,
                                c -> c));



        int gtRow = pivotGrandTotalRow(pivotCells, 21);
        Map<Integer, CellData> grandTotalRow =
                pivotCells.stream()
                        .filter(c -> c.getRowIdx() == gtRow)
                        .collect(Collectors.toMap(
                                CellData::getColIdx,
                                c -> c));

        for (Integer col : headerRow.keySet()) {

            CellData header = headerRow.get(col);

            if (header == null) {
                continue;
            }

            String date =
                    safe(header.getRawValue());

            if (!date.contains("-")) {
                continue;
            }

            CellData totalCell =
                    grandTotalRow.get(col);

            if (totalCell == null) {
                continue;
            }

            totals.put(
                    date,
                    parseDouble(totalCell.getRawValue())
            );
        }

        return totals;
    }


    private Map<String, Double> extractTimesheetEmployeeDateTotals(
            List<CellData> allCells) {

        Map<String, Double> totals = new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                allCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b)->a)));

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rows.entrySet()) {

            if (entry.getKey() == 0) {
                continue;
            }

            Map<Integer, CellData> row = entry.getValue();

            CellData dateCell = row.get(0);
            CellData nameCell = row.get(1);
            CellData hoursCell = row.get(7);

            if (dateCell == null ||
                    nameCell == null ||
                    hoursCell == null) {
                continue;
            }

            String date =
                    safe(dateCell.getRawValue());

            String employee =
                    normalizeName(nameCell.getRawValue());

            double hours =
                    parseDouble(hoursCell.getRawValue());

            String key =
                    employee + "|" + date;

            totals.merge(
                    key,
                    hours,
                    Double::sum
            );
        }

        return totals;
    }


    private String pivotDateToIso(String pivotDate) {

        try {

            String datePart =
                    pivotDate.split("\\s+")[0];

            LocalDate date =
                    LocalDate.parse(
                            datePart,
                            DateTimeFormatter.ofPattern(
                                    "dd-MMM-yy",
                                    Locale.ENGLISH));

            return date.toString();
        }
        catch (Exception e) {
            return "";
        }
    }

    private Map<String, Double> extractPivotEmployeeDateTotals(
            List<CellData> pivotCells) {

        Map<String, Double> totals =
                new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b)->a)));

        Map<Integer, String> dateColumns =
                new HashMap<>();

//        Map<Integer, CellData> headerRow = rows.get(3);
//
//
//        if (headerRow == null) {
//            return totals;
//        }



        PivotLayout layout = findPivotLayout(pivotCells);

        if (layout == null) {
            return totals;
        }

        int headerRowIndex =
                layout.getHeaderRow();

        Map<Integer, CellData> headerRow =
                rows.get(headerRowIndex);

        if (headerRow == null) {
            return totals;
        }



        for (Map.Entry<Integer, CellData> entry
                : headerRow.entrySet()) {

            int col =
                    entry.getKey();

            String value =
                    safe(entry.getValue().getDisplayValue());

//            if (value.contains("-")) {
//
//                dateColumns.put(
//                        col,
//                        pivotDateToIso(value));
//            }

            if (value.contains("-")) {

                String isoDate =
                        pivotDateToIso(value);

                dateColumns.put(col, isoDate);

                pivotDateColumns.put(
                        isoDate,
                        col
                );
            }
        }

        int gtRow = pivotGrandTotalRow(pivotCells, 21);
        for (Map.Entry<Integer, Map<Integer, CellData>> rowEntry
                : rows.entrySet()) {

            int rowIdx =
                    rowEntry.getKey();

//            if (rowIdx <= 3 || rowIdx >= gtRow) {
//                continue;
//            }

            if (rowIdx <= headerRowIndex || rowIdx >= gtRow) {
                continue;
            }

            Map<Integer, CellData> row =
                    rowEntry.getValue();

            CellData employeeCell =
                    row.get(0);

            if (employeeCell == null) {
                continue;
            }

            String employee =
                    normalizeName(
                            employeeCell.getDisplayValue());

            if (employee.isEmpty()) {
                continue;
            }

            for (Map.Entry<Integer, String> dateEntry
                    : dateColumns.entrySet()) {

                int col =
                        dateEntry.getKey();

                String date =
                        dateEntry.getValue();

                CellData valueCell =
                        row.get(col);

                if (valueCell == null) {
                    continue;
                }

                double hours =
                        parseDouble(
                                valueCell.getDisplayValue());

                String key =
                        employee + "|" + date;

                totals.put(
                        key,
                        hours);
            }
        }

        return totals;
    }


    private Double extractPivotGrandTotal(
            List<CellData> pivotCells) {

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b)->a)));

//        Map<Integer, CellData> grandTotalRow = rows.get(21);

        int gtRow =
                pivotGrandTotalRow(
                        pivotCells,
                        21
                );

        Map<Integer, CellData> grandTotalRow =
                rows.get(gtRow);


        if (grandTotalRow == null) {
            return null;
        }

        CellData totalCell =
                grandTotalRow.get(24);

        if (totalCell == null) {
            return null;
        }

        return parseDouble(
                totalCell.getDisplayValue());
    }

    private Double calculatePivotDateColumnTotal(
            List<CellData> pivotCells) {

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b)->a)));

//        Map<Integer, CellData> grandTotalRow = rows.get(21);

        int gtRow =
                pivotGrandTotalRow(
                        pivotCells,
                        21
                );

        Map<Integer, CellData> grandTotalRow =
                rows.get(gtRow);


        if (grandTotalRow == null) {
            return 0.0;
        }

        double total = 0;

        for (int col = 1; col <= 23; col++) {

            CellData cell =
                    grandTotalRow.get(col);

            if (cell == null) {
                continue;
            }

            total += parseDouble(
                    cell.getDisplayValue());
        }

        return total;
    }


    private int findWorkingDaysColumn(List<CellData> pivotCells) {

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a, b) -> a)));

        // Header row (Row Labels)
//        Map<Integer, CellData> headerRow = rows.get(3);

        PivotLayout layout = findPivotLayout(pivotCells);

        Map<Integer, CellData> headerRow = rows.get(layout.getHeaderRow());

        if (headerRow == null) {
            return -1;
        }

        int lastColumn = headerRow.keySet()
                .stream()
                .max(Integer::compareTo)
                .orElse(-1);

        // Working Days column is immediately after the last date/Grand Total column
        return lastColumn + 1;
    }


    private Map<String, Double> extractPivotEmployeeDays(
            List<CellData> pivotCells) {

        Map<String, Double> result =
                new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b)->a)));

//        int gtRow = pivotGrandTotalRow(pivotCells, 21);


//        Integer grandTotalColumn = findPivotGrandTotalColumn(pivotCells);
//
//        if (grandTotalColumn == null) {
//            return Collections.emptyMap();
//        }
//
//        int workingDaysColumn = grandTotalColumn + 1;
//
//        log.info(
//                "Grand Total Column={}, Working Days Column={}",
//                grandTotalColumn,
//                workingDaysColumn
//        );


        PivotLayout layout = findPivotLayout(pivotCells);

        if (layout == null) {
            return Collections.emptyMap();
        }

        int headerRow = layout.getHeaderRow();
        int workingDaysColumn =
                layout.getGrandTotalColumn() + 1;

        log.info(
                "Header Row={}, GrandTotal Column={}, WorkingDays Column={}",
                headerRow,
                layout.getGrandTotalColumn(),
                workingDaysColumn
        );




        for (Map.Entry<Integer, Map<Integer, CellData>> rowEntry
                : rows.entrySet()) {

            int rowIdx =
                    rowEntry.getKey();


//            if (rowIdx <= 3 || rowIdx >= 21) {
//                continue;
//            }

            if (rowIdx <= headerRow) {
                continue;
            }

            Map<Integer, CellData> row =
                    rowEntry.getValue();

            log.info("ROW {} COLUMNS {}", rowIdx, row.keySet());

            for (Map.Entry<Integer, CellData> cell : row.entrySet()) {

                log.info(
                        "ROW={} COL={} VALUE={}",
                        rowIdx,
                        cell.getKey(),
                        cell.getValue().getDisplayValue()
                );
            }


            CellData employeeCell =
                    row.get(0);

//            CellData daysCell = row.get(12);

//            int workingDaysColumn = findWorkingDaysColumn(pivotCells);

            CellData daysCell = row.get(workingDaysColumn);

            log.info(
                    "employeeCell={} daysCell={}",
                    employeeCell == null ? "NULL" : employeeCell.getDisplayValue(),
                    daysCell == null ? "NULL" : daysCell.getDisplayValue()
            );


            if (employeeCell == null || daysCell == null) {
                continue;
            }

            String employee =
                    normalizeName(
                            employeeCell.getDisplayValue());

            if ("grand total".equalsIgnoreCase(employee)) {
                break;
            }

            log.info("Normalized employee = '{}'", employee);

            double days =
                    parseDouble(
                            daysCell.getDisplayValue());

            log.info("Parsed days = {}", days);

            log.info(
                    "ADDING employee='{}' days={}",
                    employee,
                    days
            );



            result.put(employee, days);
        }
        log.info("FINAL RESULT = {}", result);
        return result;
    }



    private Map<String, Integer> extractPivotEmployeeRows(
            List<CellData> pivotCells) {

        Map<String, Integer> employeeRows =
                new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a, b) -> a)));

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rows.entrySet()) {

            Integer rowNumber =
                    entry.getKey();

            Map<Integer, CellData> row =
                    entry.getValue();

            CellData employeeCell =
                    row.get(0);

            if (employeeCell == null) {
                continue;
            }

            String employee =
                    normalizeName(employeeCell.getRawValue());

//            if (employee.isBlank()
//                    || employee.equals("row labels")
//                    || employee.equals("grand total")) {
//                continue;
//            }

            if (employee.isBlank()
                    || isRowLabelHeader(employee)
                    || employee.equals("grand total")) {
                continue;
            }

//            int displayRow = rowNumber - 2;
//
//            employeeRows.put(employee, displayRow);

//            employeeRows.put(employee, rowNumber);

            employeeRows.put(employee, rowNumber);

//            log.info(
//                    "EMPLOYEE={} EXCEL={}",
//                    employee,
//                    rowNumber
//            );


        }

        return employeeRows;
    }


    private Map<String, Integer> extractPivotDateColumns(
            List<CellData> pivotCells) {

        Map<String, Integer> result =
                new HashMap<>();

        Map<Integer, Map<Integer, CellData>> rows =
                pivotCells.stream()
                        .collect(Collectors.groupingBy(
                                CellData::getRowIdx,
                                Collectors.toMap(
                                        CellData::getColIdx,
                                        c -> c,
                                        (a,b) -> a)));

//        Map<Integer, CellData> headerRow =
//                rows.get(3);
//
//        if (headerRow == null) {
//            return result;
//        }


        PivotLayout layout = findPivotLayout(pivotCells);

        if (layout == null) {
            return result;
        }

        Map<Integer, CellData> headerRow =
                rows.get(layout.getHeaderRow());

        if (headerRow == null) {
            return result;
        }


        for (Map.Entry<Integer, CellData> entry
                : headerRow.entrySet()) {

            int col =
                    entry.getKey();

            String value =
                    safe(entry.getValue().getDisplayValue());

            if (value.contains("-")) {

                result.put(
                        pivotDateToIso(value),
                        col
                );
            }
        }

        return result;
    }

    private boolean isRowLabelHeader(String value) {

        if (value == null) {
            return false;
        }

        String normalized =
                value.trim().toLowerCase();

        return normalized.equals("row labels")
                || normalized.equals("row lables")
                || normalized.equals("name")
                || normalized.equals("name (mandatory)");
    }


    private PivotLayout findPivotLayout(List<CellData> pivotCells) {

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : pivotCells) {

            rowMap.computeIfAbsent(
                    c.getRowIdx(),
                    k -> new TreeMap<>()
            ).put(c.getColIdx(), c);
        }

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

            for (CellData cell : entry.getValue().values()) {

                if ("Grand Total".equalsIgnoreCase(
                        cell.getDisplayValue())) {

                    return new PivotLayout(
                            entry.getKey(),
                            cell.getColIdx()
                    );
                }
            }
        }

        return null;
    }

    private static class PivotLayout {

        private final int headerRow;
        private final int grandTotalColumn;

        PivotLayout(int headerRow, int grandTotalColumn) {
            this.headerRow = headerRow;
            this.grandTotalColumn = grandTotalColumn;
        }

        public int getHeaderRow() {
            return headerRow;
        }

        public int getGrandTotalColumn() {
            return grandTotalColumn;
        }
    }


    private String valRaw(Map<Integer, CellData> cols, int col) {
        CellData c = cols.get(col);
        if (c == null) return "";
        String raw = c.getRawValue();
        if (raw != null && !raw.isBlank()) return raw.trim();
        return c.getDisplayValue() == null ? "" : c.getDisplayValue().trim();
    }



    private Map<ProjectKey, Double> extractTimesheetProjectTotals(List<CellData> timesheetCells) {

        TreeMap<Integer, Map<Integer, CellData>> rowMap =
                new TreeMap<>();

        for (CellData c : timesheetCells) {

                rowMap.computeIfAbsent(
                        c.getRowIdx(),
                        k -> new TreeMap<>())
                        .put(c.getColIdx(), c);
        }

        int headerRow = rowMap.firstKey();

        Map<ProjectKey, Double> totals =
                new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, CellData>> entry
                : rowMap.entrySet()) {

                if (entry.getKey() == headerRow) {
                continue;
                }

                Map<Integer, CellData> cols =
                        entry.getValue();

                String project =
                        val(cols, 3);

                String hours =
                        val(cols, 7);

                if (project.isBlank() || hours.isBlank()) {
                continue;
                }

                totals.merge(
                        new ProjectKey(project),
                        parseDouble(hours),
                        Double::sum);
        }

                log.info("==========================================");
                log.info("PROJECT CODE TOTALS");
                totals.forEach((k, v) ->
                        log.info("{} -> {}", k, v));
                log.info("==========================================");
                return totals;
        }


        private Map<SubProjectKey, Double> extractTimesheetSubProjectTotals(
        List<CellData> timesheetCells) {

    TreeMap<Integer, Map<Integer, CellData>> rowMap =
            new TreeMap<>();

    for (CellData c : timesheetCells) {

        rowMap.computeIfAbsent(
                c.getRowIdx(),
                k -> new TreeMap<>())
                .put(c.getColIdx(), c);
    }

    int headerRow = rowMap.firstKey();

    Map<SubProjectKey, Double> totals =
            new HashMap<>();

    for (Map.Entry<Integer, Map<Integer, CellData>> entry
            : rowMap.entrySet()) {

        if (entry.getKey() == headerRow) {
            continue;
        }

        Map<Integer, CellData> cols =
                entry.getValue();

        String project =
                val(cols, 3);

        String subProject =
                val(cols, 4);

        String hours =
                val(cols, 7);

        if (project.isBlank()
                || subProject.isBlank()
                || hours.isBlank()) {

            continue;
        }

        totals.merge(
                new SubProjectKey(project, subProject),
                parseDouble(hours),
                Double::sum);
    }

    return totals;
}


private Map<ProjectCodeKey, Double> extractTimesheetProjectCodeTotals(
        List<CellData> timesheetCells) {

    TreeMap<Integer, Map<Integer, CellData>> rowMap =
            new TreeMap<>();

    for (CellData c : timesheetCells) {

        rowMap.computeIfAbsent(
                c.getRowIdx(),
                k -> new TreeMap<>())
                .put(c.getColIdx(), c);
    }

    int headerRow = rowMap.firstKey();

    Map<ProjectCodeKey, Double> totals =
            new HashMap<>();

    for (Map.Entry<Integer, Map<Integer, CellData>> entry
            : rowMap.entrySet()) {

        if (entry.getKey() == headerRow) {
            continue;
        }

        Map<Integer, CellData> cols =
                entry.getValue();

        String project =
                val(cols, 3);

        String subProject =
                val(cols, 4);

        String projectCode =
                val(cols, 5);

        String hours =
                val(cols, 7);

        if (project.isBlank()
                || subProject.isBlank()
                || projectCode.isBlank()
                || hours.isBlank()) {

            continue;
        }

        double rowHours = parseDouble(hours);

        ProjectCodeKey key = new ProjectCodeKey(
                project,
                subProject,
                projectCode);

        log.info(
                "PW-003 ROW -> Project='{}', SubProject='{}', ProjectCode='{}', Hours={}",
                project,
                subProject,
                projectCode,
                rowHours);

        totals.merge(
                key,
                rowHours,
                Double::sum);
    }

    return totals;
}


private ValidationIssue projectWiseIssue(
        String sid,
        String ruleId,
        String severity,
        int row,
        int col,
        String field,
        String msg) {

    return ValidationIssue.builder()
            .sessionId(sid)
            .ruleId(ruleId)
            .severity(severity)
            .sheetName(PROJECT_WISE_SHEET)
            .rowIdx(row)
            .colIdx(col)
            .fieldName(field)
            .message(renderMessage(
                    ruleId,
                    severity,
                    field,
                    msg))
            .build();
}

        private ValidationIssue projectWiseIssue(
                String sid,
                String ruleId,
                String severity,
                CellReference cell,
                String message) {

        return projectWiseIssue(
                sid,
                ruleId,
                severity,
                cell.getRow(),
                cell.getColumn(),
                cell.getFieldName(),
                message);
        }


/**
 * Performs Project Wise validation.
 *
 * PW-001 : Project totals
 * PW-002 : Sub Project totals
 * PW-003 : Project Code totals
 */
private void validateProjectWise(
        String sessionId,
        List<CellData> timesheetCells,
        List<CellData> projectWiseCells,
        List<ValidationIssue> issues) {

    log.info("==================================================");
    log.info("Starting Project Wise Validation");
    log.info("==================================================");

    if (projectWiseCells == null || projectWiseCells.isEmpty()) {

        log.warn("Project Wise sheet not found. Skipping validation.");
        return;
    }

    ProjectWiseHierarchy hierarchy =
            projectWiseParser.parse(projectWiseCells);

    if (hierarchy.isEmpty()) {

        log.warn("Project Wise sheet contains no parsable data.");
        return;
    }

    Map<ProjectKey, Double> projectTotals =
            extractTimesheetProjectTotals(timesheetCells);

    Map<SubProjectKey, Double> subProjectTotals =
            extractTimesheetSubProjectTotals(timesheetCells);

    Map<ProjectCodeKey, Double> projectCodeTotals =
            extractTimesheetProjectCodeTotals(timesheetCells);

    log.info("Projects parsed      : {}", hierarchy.getProjects().size());
    log.info("Sub Projects parsed  : {}", hierarchy.getSubProjects().size());
    log.info("Project Codes parsed : {}", hierarchy.getProjectCodes().size());

    validateProjects(
            sessionId,
            hierarchy,
            projectTotals,
            issues);

    validateSubProjects(
            sessionId,
            hierarchy,
            subProjectTotals,
            issues);

    validateProjectCodes(
            sessionId,
            hierarchy,
            projectCodeTotals,
            issues);

    log.info("Project Wise Validation completed.");
}



/**
 * PW-001
 *
 * Validates Project totals between the Project Wise sheet
 * and the Timesheet sheet.
 */
private void validateProjects(
        String sessionId,
        ProjectWiseHierarchy hierarchy,
        Map<ProjectKey, Double> projectTotals,
        List<ValidationIssue> issues) {

    log.info("Starting Project validation...");

    for (ProjectSummary project : hierarchy.getProjects()) {

        ProjectKey key = new ProjectKey(
                project.getProjectName());

        double actualHours = project.getHours();

        double expectedHours = projectTotals.getOrDefault(
                key,
                0d);

        if (Double.compare(expectedHours, actualHours) != 0) {

            log.warn(
                    "PW-001 failed. Project='{}', Expected={}, Actual={}",
                    project.getProjectName(),
                    expectedHours,
                    actualHours);

            issues.add(
                projectWiseIssue(
                        sessionId,
                        "PW-001",
                        "CRITICAL",
                        project.getHoursCell(),
                        String.format(
                                "Project '%s' hours mismatch. Expected %.2f hours but found %.2f hours.",
                                project.getProjectName(),
                                expectedHours,
                                actualHours)));
        }
    }

    log.info("Completed Project validation.");
}


/**
 * PW-002
 *
 * Validates Sub Project totals between the Project Wise sheet
 * and the Timesheet sheet.
 */
private void validateSubProjects(
        String sessionId,
        ProjectWiseHierarchy hierarchy,
        Map<SubProjectKey, Double> subProjectTotals,
        List<ValidationIssue> issues) {

    log.info("Starting Sub Project validation...");

    for (SubProjectSummary subProject : hierarchy.getSubProjects()) {

        SubProjectKey key =
                new SubProjectKey(
                        subProject.getProjectName(),
                        subProject.getSubProjectName());

        double actualHours =
                subProject.getHours();

        double expectedHours =
                subProjectTotals.getOrDefault(
                        key,
                        0d);

        if (Double.compare(expectedHours, actualHours) != 0) {

            log.warn(
                    "PW-002 failed. Project='{}', SubProject='{}', Expected={}, Actual={}",
                    subProject.getProjectName(),
                    subProject.getSubProjectName(),
                    expectedHours,
                    actualHours);

            issues.add(
                projectWiseIssue(
                        sessionId,
                        "PW-002",
                        "CRITICAL",
                        subProject.getHoursCell(),
                        String.format(
                                "Sub Project '%s' under Project '%s' has incorrect hours. Expected %.2f hours but found %.2f hours.",
                                subProject.getSubProjectName(),
                                subProject.getProjectName(),
                                expectedHours,
                                actualHours
                        )));
        }
    }

    log.info("Completed Sub Project validation.");
}


        /**
         * PW-003
         *
         * Validates Project Code totals between the Project Wise sheet
         * and the Timesheet sheet.
         */
        private void validateProjectCodes(
                String sessionId,
                ProjectWiseHierarchy hierarchy,
                Map<ProjectCodeKey, Double> projectCodeTotals,
                List<ValidationIssue> issues) {

        log.info("Starting Project Code validation...");

        for (ProjectCodeSummary projectCode
                : hierarchy.getProjectCodes()) {

                ProjectCodeKey key =
                        new ProjectCodeKey(
                                projectCode.getProjectName(),
                                projectCode.getSubProjectName(),
                                projectCode.getProjectCode());

                double actualHours =
                        projectCode.getHours();

                double expectedHours =
                        projectCodeTotals.getOrDefault(
                                key,
                                0d);


                log.info(
                "COMPARE -> key={} expected={} actual={}",
                key,
                expectedHours,
                actualHours);

                if (Double.compare(expectedHours, actualHours) != 0) {

                log.warn(
                        "PW-003 failed. Project='{}', SubProject='{}', ProjectCode='{}', Expected={}, Actual={}",
                        projectCode.getProjectName(),
                        projectCode.getSubProjectName(),
                        projectCode.getProjectCode(),
                        expectedHours,
                        actualHours);

                issues.add(
                        projectWiseIssue(
                                sessionId,
                                "PW-003",
                                "CRITICAL",
                                projectCode.getHoursCell(),
                                String.format(
                                        "Project Code '%s' under Sub Project '%s' of Project '%s' has incorrect hours. Expected %.2f hours but found %.2f hours.",
                                        projectCode.getProjectCode(),
                                        projectCode.getSubProjectName(),
                                        projectCode.getProjectName(),
                                        expectedHours,
                                        actualHours
                                )));
                }
        }

        log.info("Completed Project Code validation.");
        }





}
