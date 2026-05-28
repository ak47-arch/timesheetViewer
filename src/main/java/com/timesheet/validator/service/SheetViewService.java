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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        Map<String, Map<Integer, Map<Integer, ValidationIssue>>> issueMap = buildIssueMap(sessionId);

        List<SheetDto> result = new ArrayList<>();
        for (SheetMeta meta : metas) {
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

                List<CellDto> row = new ArrayList<>();
                for (int ci = 0; ci < maxCol; ci++) {
                    CellData c = colMap.get(ci);
                    String display = c != null ? nvl(c.getDisplayValue()) : "";
                    String formula = c != null ? c.getFormula() : null;
                    String type    = c != null ? nvl(c.getCellType()) : "BLANK";
                    boolean header = c != null && Boolean.TRUE.equals(c.getIsHeader());

                    // Overlay validation issue if any
                    ValidationIssue issue = getIssue(issueMap, meta.getSheetName(), ri, ci);
                    String validationMsg = issue != null ? issue.getMessage() : null;
                    String severity      = issue != null ? issue.getSeverity() : null;

                    // Build formula tooltip
                    String tooltip = buildTooltip(formula, display, type);

                    row.add(CellDto.builder()
                            .rowIdx(ri).colIdx(ci)
                            .displayValue(display)
                            .formula(tooltip)
                            .cellType(type)
                            .isHeader(header)
                            .validationMsg(validationMsg)
                            .severity(severity)
                            .build());
                }
                rows.add(row);
            }

            result.add(SheetDto.builder()
                    .sheetName(meta.getSheetName())
                    .sheetIndex(meta.getSheetIndex())
                    .rowCount(meta.getRowCount())
                    .colCount(maxCol)
                    .rows(rows)
                    .build());
        }
        return result;
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

    private Map<String, Map<Integer, Map<Integer, ValidationIssue>>> buildIssueMap(String sessionId) {
        List<ValidationIssue> issues = issueRepo.findBySessionId(sessionId);
        Map<String, Map<Integer, Map<Integer, ValidationIssue>>> map = new HashMap<>();
        for (ValidationIssue issue : issues) {
            if (issue.getRowIdx() == null || issue.getColIdx() == null
                    || issue.getRowIdx() < 0 || issue.getColIdx() < 0) continue;
            map.computeIfAbsent(issue.getSheetName(), k -> new HashMap<>())
               .computeIfAbsent(issue.getRowIdx(), k -> new HashMap<>())
               .put(issue.getColIdx(), issue);
        }
        return map;
    }

    private ValidationIssue getIssue(
            Map<String, Map<Integer, Map<Integer, ValidationIssue>>> map,
            String sheet, int row, int col) {
        return Optional.ofNullable(map.get(sheet))
                .map(r -> r.get(row))
                .map(c -> c.get(col))
                .orElse(null);
    }

    private String nvl(String s) { return s == null ? "" : s; }
}
