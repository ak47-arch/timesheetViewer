package com.timesheet.validator.service;

import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.domain.LeaveEntry;
import com.timesheet.validator.domain.Resource;
import com.timesheet.validator.repository.CellDataRepository;
import com.timesheet.validator.repository.LeaveEntryRepository;
import com.timesheet.validator.repository.ResourceRepository;
import com.timesheet.validator.repository.SheetMetaRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an admin-uploaded Leave Planner workbook — which the existing
 * pipeline has already shredded into CELL_DATA / SHEET_META — into
 * durable, queryable {@link LeaveEntry} rows (source = ADMIN_PLANNER),
 * so admin-loaded leaves appear on every user's calendar.
 *
 * <p>Tuned against the real "Sydney SoftDev Leave Planner" template:</p>
 * <ul>
 *   <li>Monthly sheets are {@code MMM'yy} (Jan'26 … Sep'26). Support
 *       sheets (Count, Manage, Incentive) are skipped.</li>
 *   <li>The <b>date row</b> ("Date" in column A, real dates across the
 *       columns) sits <b>one row above</b> the {@code Name/Day}+{@code Team}
 *       header row (whose cells are weekday names). Date columns are read
 *       from that date row, using the raw ISO value the parser stores.</li>
 *   <li>Only the codes in {@link #LEAVE_CODES} are personal leave
 *       (PL/UL and half-day L1/L2). {@code H} (public holiday — already in
 *       the system), {@code Weekend}, {@code Left}, blanks, numbers, sprint
 *       names and the COUNTIF summary columns are all ignored.</li>
 *   <li>Employee rows run from just below the header to the first blank /
 *       gap row; the footer legend below is not scanned.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeavePlannerImportService {

    private final CellDataRepository    cellRepo;
    private final SheetMetaRepository   sheetMetaRepo;
    private final ResourceRepository    resourceRepo;
    private final LeaveEntryRepository  leaveRepo;

    private static final Pattern MONTH_SHEET =
            Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)'(\\d{2})$");

    /** Personal-leave codes → human labels. Everything else is not leave. */
    private static final Map<String, String> LEAVE_CODES = Map.of(
            "PL", "Planned Leave",
            "UL", "Unplanned Leave",
            "L1", "Morning Leave",
            "L2", "Afternoon Leave");

    private static final Pattern PAREN_SUFFIX = Pattern.compile("\\s*\\(.*\\)\\s*$");
    private static final List<DateTimeFormatter> DATE_FMTS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                          // 2026-01-01  (raw)
            DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),  // 01-Jan-26   (display, suffix stripped)
            DateTimeFormatter.ofPattern("d-MMM-yy",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy",Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MM/yyyy"));

    /** display + raw text for one cell. */
    private record Cell(String display, String raw) {
        String text() { return (display != null && !display.isBlank()) ? display : (raw == null ? "" : raw); }
    }

    @Getter
    public static class ImportSummary {
        private final int leaveDaysImported;
        private final int monthlySheets;
        private final Set<String> unmatchedNames;
        public ImportSummary(int days, int sheets, Set<String> unmatched) {
            this.leaveDaysImported = days; this.monthlySheets = sheets; this.unmatchedNames = unmatched;
        }
    }

    /**
     * Replaces all previously-imported planner leaves with the leaves in the
     * given upload session. User-registered leaves are left untouched.
     */
    @Transactional
    public ImportSummary importFromSession(String sessionId) {

        leaveRepo.deleteBySource(LeaveEntry.SOURCE_ADMIN);   // idempotent re-import

        Map<String, Resource> resourceByName = new HashMap<>();
        for (Resource r : resourceRepo.findAll()) resourceByName.put(normalizeName(r.getName()), r);

        int days = 0, sheets = 0;
        Set<String> unmatched = new LinkedHashSet<>();

        List<String> sheetNames = sheetMetaRepo.findBySessionIdOrderBySheetIndex(sessionId)
                .stream().map(m -> m.getSheetName()).toList();

        for (String sheetName : sheetNames) {
            Matcher ms = MONTH_SHEET.matcher(sheetName.trim());
            if (!ms.matches()) continue;                     // skip Count / Manage / Incentive
            sheets++;

            Month month = Month.of(monthIndex(ms.group(1)));
            int year = 2000 + Integer.parseInt(ms.group(2));

            List<CellData> cells = cellRepo
                    .findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(sessionId, sheetName);
            if (cells.isEmpty()) continue;

            TreeMap<Integer, TreeMap<Integer, Cell>> grid = new TreeMap<>();
            for (CellData c : cells) {
                grid.computeIfAbsent(c.getRowIdx(), k -> new TreeMap<>())
                    .put(c.getColIdx(), new Cell(trim(c.getDisplayValue()), trim(c.getRawValue())));
            }

            int headerRow = findHeaderRow(grid);
            if (headerRow < 0) { log.warn("[LeaveImport] no header row in '{}'", sheetName); continue; }

            int nameCol = nameColumn(grid.get(headerRow));
            int dateRow = findDateRow(grid, headerRow, month, year);
            Map<Integer, LocalDate> dateCols = dateColumns(grid.get(dateRow), month, year);
            if (nameCol < 0 || dateCols.isEmpty()) {
                log.warn("[LeaveImport] '{}' nameCol={} dateCols={}", sheetName, nameCol, dateCols.size());
                continue;
            }

            boolean started = false;
            int prevRow = headerRow;
            for (Map.Entry<Integer, TreeMap<Integer, Cell>> rowE : grid.tailMap(headerRow + 1).entrySet()) {
                int rowIdx = rowE.getKey();
                if (started && rowIdx - prevRow > 1) break;   // blank-row gap → end of employee block

                Map<Integer, Cell> row = rowE.getValue();
                String name = cellText(row, nameCol);
                if (name.isBlank()) { if (started) break; else { continue; } }

                started = true; prevRow = rowIdx;

                Resource res = resourceByName.get(normalizeName(name));
                if (res == null) { unmatched.add(name); continue; }

                for (Map.Entry<Integer, LocalDate> dc : dateCols.entrySet()) {
                    String code = cellText(row, dc.getKey()).toUpperCase(Locale.ROOT);
                    String label = LEAVE_CODES.get(code);
                    if (label != null && persist(res, dc.getValue(), label, code)) days++;
                }
            }
        }

        log.info("[LeaveImport] session={} sheets={} leaveDays={} unmatched={}",
                sessionId, sheets, days, unmatched);
        return new ImportSummary(days, sheets, unmatched);
    }

    private boolean persist(Resource res, LocalDate date, String label, String code) {
        if (leaveRepo.existsByResourceIdAndLeaveDateAndSource(
                res.getResourceId(), date, LeaveEntry.SOURCE_ADMIN)) return false;
        leaveRepo.save(LeaveEntry.builder()
                .resourceId(res.getResourceId())
                .resourceName(res.getName())
                .leaveDate(date)
                .leaveType(label)
                .status(LeaveEntry.STATUS_APPROVED)
                .source(LeaveEntry.SOURCE_ADMIN)
                .reason("Leave Planner: " + code)
                .createdBy("ADMIN_IMPORT")
                .createdAt(LocalDateTime.now())
                .build());
        return true;
    }

    // ── header / row / column detection ────────────────────────────────────────

    private int findHeaderRow(TreeMap<Integer, TreeMap<Integer, Cell>> grid) {
        for (Map.Entry<Integer, TreeMap<Integer, Cell>> e : grid.entrySet()) {
            boolean hasName = false, hasTeam = false;
            for (Cell c : e.getValue().values()) {
                String n = normalize(c.text());
                if (n.equals("employee name") || n.equals("name/day")) hasName = true;
                if (n.equals("team")) hasTeam = true;
            }
            if (hasName && hasTeam) return e.getKey();
        }
        return -1;
    }

    private int nameColumn(Map<Integer, Cell> header) {
        for (Map.Entry<Integer, Cell> e : header.entrySet()) {
            String n = normalize(e.getValue().text());
            if (n.equals("employee name") || n.equals("name/day")) return e.getKey();
        }
        return -1;
    }

    /**
     * The date row sits at/above the header row. Prefer a row whose column-A
     * label is "Date" and which has real dates; otherwise the row (up to the
     * header) with the most date-parseable cells.
     */
    private int findDateRow(TreeMap<Integer, TreeMap<Integer, Cell>> grid, int headerRow,
                            Month month, int year) {
        int best = headerRow, bestCount = -1;
        for (Map.Entry<Integer, TreeMap<Integer, Cell>> e : grid.headMap(headerRow, true).entrySet()) {
            TreeMap<Integer, Cell> row = e.getValue();
            int count = (int) row.values().stream()
                    .filter(c -> parseDate(c, month, year) != null).count();
            String a = row.isEmpty() ? "" : normalize(row.firstEntry().getValue().text());
            if (a.equals("date") && count > 0) return e.getKey();
            if (count > bestCount) { bestCount = count; best = e.getKey(); }
        }
        return best;
    }

    private Map<Integer, LocalDate> dateColumns(Map<Integer, Cell> dateRow, Month month, int year) {
        Map<Integer, LocalDate> out = new LinkedHashMap<>();
        if (dateRow == null) return out;
        for (Map.Entry<Integer, Cell> e : dateRow.entrySet()) {
            LocalDate d = parseDate(e.getValue(), month, year);
            if (d != null) out.put(e.getKey(), d);
        }
        return out;
    }

    /** Parse a date cell: raw ISO first, then display (suffix stripped), then bare day number. */
    private LocalDate parseDate(Cell cell, Month month, int year) {
        if (cell == null) return null;

        for (String candidate : new String[]{cell.raw(), stripSuffix(cell.display())}) {
            if (candidate == null || candidate.isBlank()) continue;
            for (DateTimeFormatter f : DATE_FMTS) {
                try { return LocalDate.parse(candidate, f); } catch (Exception ignored) { }
            }
        }
        // Bare day number ("1", "1.0") → combine with the sheet's month/year.
        String s = cell.text();
        try {
            int day = (int) Double.parseDouble(s);
            if (day >= 1 && day <= month.length(Year.of(year).isLeap())) return LocalDate.of(year, month, day);
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private String stripSuffix(String s) { return s == null ? null : PAREN_SUFFIX.matcher(s).replaceAll(""); }

    private String cellText(Map<Integer, Cell> row, int col) {
        Cell c = row.get(col);
        return c == null ? "" : c.text().trim();
    }

    private int monthIndex(String abbrev) {
        return switch (abbrev) {
            case "Jan" -> 1; case "Feb" -> 2;  case "Mar" -> 3;  case "Apr" -> 4;
            case "May" -> 5; case "Jun" -> 6;  case "Jul" -> 7;  case "Aug" -> 8;
            case "Sep" -> 9; case "Oct" -> 10; case "Nov" -> 11; default -> 12;
        };
    }

    private String trim(String s) { return s == null ? "" : s.trim(); }

    /** For header/label detection — keeps '/', collapses spaces (e.g. "name/day"). */
    private String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** For person matching — strips all non-alphanumerics, matching LeaveCalendarService. */
    private String normalizeName(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
