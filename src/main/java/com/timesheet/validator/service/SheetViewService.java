package com.timesheet.validator.service;

import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.domain.SheetMeta;
import com.timesheet.validator.domain.ValidationIssue;
import com.timesheet.validator.dto.CellDto;
import com.timesheet.validator.dto.SheetDto;
import com.timesheet.validator.repository.CellDataRepository;
import com.timesheet.validator.repository.SheetMetaRepository;
import com.timesheet.validator.repository.ValidationIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SheetViewService {

    private final SheetMetaRepository sheetMetaRepo;
    private final CellDataRepository cellDataRepo;
    private final ValidationIssueRepository issueRepo;

    /**
     * Returns all sheets for the session, each with a 2D grid of CellDto.
     * Validation issues are overlaid on matching cells.
     */
    public List<SheetDto> getSheets(String sessionId) {
        List<SheetMeta> metas = sheetMetaRepo.findBySessionIdOrderBySheetIndex(sessionId);

        // Build an issue lookup: sheetName → rowIdx → colIdx → issue
        Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> issueMap = buildIssueMap(sessionId);

        List<SheetDto> result = new ArrayList<>();
        for (SheetMeta meta : metas) {
            result.add(buildSheet(sessionId, meta, issueMap));
        }
        return result;
    }

    /**
     * Builds a single sheet's grid on demand. Backs the lazy per-tab API so the
     * viewer no longer has to serialise every sheet into one giant page.
     */
    public SheetDto getSheet(String sessionId, int sheetIndex) {
        SheetMeta meta = sheetMetaRepo.findBySessionIdOrderBySheetIndex(sessionId).stream()
                .filter(m -> m.getSheetIndex() != null && m.getSheetIndex() == sheetIndex)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Sheet index " + sheetIndex + " not found for session " + sessionId));
        return buildSheet(sessionId, meta, buildIssueMap(sessionId));
    }

    private SheetDto buildSheet(String sessionId, SheetMeta meta,
                               Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> issueMap) {
            List<CellData> cells = cellDataRepo
                    .findBySessionIdAndSheetNameOrderByRowIdxAscColIdxAsc(sessionId, meta.getSheetName());

            // Group by rowIdx
            Map<Integer, List<CellData>> byRow = cells.stream()
                    .collect(Collectors.groupingBy(CellData::getRowIdx, TreeMap::new, Collectors.toList()));

            // Find max col for consistent grid width
            int maxCol = cells.stream().mapToInt(CellData::getColIdx).max().orElse(0) + 1;

            List<List<CellDto>> rows = new ArrayList<>();
            for (Map.Entry<Integer, List<CellData>> rowEntry : byRow.entrySet()) {
                int ri = rowEntry.getKey();
                Map<Integer, CellData> colMap = rowEntry.getValue().stream()
                        .collect(Collectors.toMap(CellData::getColIdx, c -> c, (a, b) -> a));


                CellData employeeCell = colMap.get(0);

                if(employeeCell != null){
                    log.info(
                            "VIEWER ROW -> rowIdx={} employee={}",
                            ri,
                            employeeCell.getDisplayValue()
                    );
                }


                List<CellDto> row = new ArrayList<>();
                for (int ci = 0; ci < maxCol; ci++) {
                    CellData c = colMap.get(ci);
                    String display = c != null ? nvl(c.getDisplayValue()) : "";
                    String formula = c != null ? c.getFormula() : null;
                    String type    = c != null ? nvl(c.getCellType()) : "BLANK";
                    boolean header = c != null && Boolean.TRUE.equals(c.getIsHeader());

                    // Overlay validation issue if any
                    List<ValidationIssue> issues =
                            getIssues(issueMap,
                                    meta.getSheetName(),
                                    ri,
                                    ci);

                    List<String> validationMessages = issues.stream()
                            .map(ValidationIssue::getMessage)
                            .collect(Collectors.toList());

                    List<String> severities = issues.stream()
                            .map(ValidationIssue::getSeverity)
                            .collect(Collectors.toList());

                    String highestSeverity = null;

                    if (severities.contains("CRITICAL")) {
                        highestSeverity = "CRITICAL";
                    }
                    else if (severities.contains("WARNING")) {
                        highestSeverity = "WARNING";
                    }

                    boolean employeeIssue =
                            ci == 0 &&
                                    issues != null &&
                                    !issues.isEmpty();

                    // Build formula tooltip
                    String tooltip = buildTooltip(formula, display, type);

                    row.add(CellDto.builder()
                            .rowIdx(ri).colIdx(ci)
                            .displayValue(display)
                            .formula(tooltip)
                            .cellType(type)
                            .isHeader(header)
                            .validationMessages(validationMessages)
                            .severities(severities)
                            .highestSeverity(highestSeverity)
                            .employeeIssue(employeeIssue)
                            .build());
                }
                rows.add(row);
            }

            return SheetDto.builder()
                    .sheetName(meta.getSheetName())
                    .sheetIndex(meta.getSheetIndex())
                    .rowCount(meta.getRowCount())
                    .colCount(maxCol)
                    .rows(rows)
                    .build();
    }

    public List<SheetMeta> getSheetMetas(String sessionId) {
        return sheetMetaRepo.findBySessionIdOrderBySheetIndex(sessionId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildTooltip(String formula, String display, String type) {
        if (formula != null && !formula.isBlank()) {
            return formula; // e.g. "=SUM(B2:B10)"
        }
        // For key business fields, annotate with what the value means
        return null;
    }

    private Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> buildIssueMap(String sessionId) {
        List<ValidationIssue> issues = issueRepo.findBySessionId(sessionId);
        Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> map = new HashMap<>();
        for (ValidationIssue issue : issues) {
            if (issue.getRowIdx() == null || issue.getColIdx() == null
                    || issue.getRowIdx() < 0 || issue.getColIdx() < 0) continue;
            map.computeIfAbsent(issue.getSheetName(), k -> new HashMap<>())
               .computeIfAbsent(issue.getRowIdx(), k -> new HashMap<>())
                    .computeIfAbsent(issue.getColIdx(),
                            k -> new ArrayList<>())
                    .add(issue);
        }
        return map;
    }

//    private List<ValidationIssue> getIssues(
//            Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> map,
//            String sheet,
//            int row,
//            int col) {
//
//        return Optional.ofNullable(map.get(sheet))
//                .map(r -> r.get(row))
//                .map(c -> c.get(col))
//                .orElse(Collections.emptyList());
//    }


    private List<ValidationIssue> getIssues(
            Map<String, Map<Integer, Map<Integer, List<ValidationIssue>>>> map,
            String sheet,
            int row,
            int col) {

        List<ValidationIssue> issues =
                Optional.ofNullable(map.get(sheet))
                        .map(r -> r.get(row))
                        .map(c -> c.get(col))
                        .orElse(Collections.emptyList());

//        if (!issues.isEmpty()) {
//
//            log.info(
//                    "CELL HIGHLIGHT -> sheet={} row={} col={} issues={}",
//                    sheet,
//                    row,
//                    col,
//                    issues.size()
//            );
//        }

        if (!issues.isEmpty()) {

            log.info(
                    "CELL HIGHLIGHT -> sheet={} row={} col={} rule={} msg={}",
                    sheet,
                    row,
                    col,
                    issues.get(0).getRuleId(),
                    issues.get(0).getMessage()
            );
        }

        return issues;
    }

    private String nvl(String s) { return s == null ? "" : s; }
}
