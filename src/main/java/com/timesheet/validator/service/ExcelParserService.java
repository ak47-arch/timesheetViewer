package com.timesheet.validator.service;

import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.domain.SheetMeta;
import com.timesheet.validator.domain.UploadSession;
import com.timesheet.validator.repository.CellDataRepository;
import com.timesheet.validator.repository.SheetMetaRepository;
import com.timesheet.validator.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelParserService {

    /** Display format shown in the viewer grid: "01-Mar-26 (Sun)" */
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    /** ISO format stored in RAW_VALUE so ValidationService can always parse it */
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Excel date serial range used to detect date values unconditionally.
     *
     * Why we cannot rely on DateUtil.isCellDateFormatted() alone:
     *   - The Excel file may have date cells formatted as "General" or a
     *     numeric format string that POI does not classify as a date pattern.
     *   - In those cases isCellDateFormatted() returns false even though the
     *     numeric value is a date serial, so DataFormatter outputs "46082.0".
     *
     * Why we cannot rely on DataFormatter alone:
     *   - DataFormatter output depends on the cell's format string, which varies
     *     by Excel locale and user customisation: "3/1/26", "01-Mar-26",
     *     "46082.0" are all possible for the same underlying date value.
     *
     * Solution — dual-gate detection:
     *   A cell is treated as a date if EITHER condition is true:
     *     (a) DateUtil.isCellDateFormatted(cell) == true   (POI date format)
     *     (b) isDateSerial(numericValue) == true           (value in range)
     *
     * The range 35000–60000 covers 1995-08-09 to 2064-03-22.
     * This safely excludes typical non-date numbers in timesheets:
     *   hours (0.5–8), rates (100–500), PO values (millions) are all outside.
     */
    private static final double DATE_SERIAL_MIN = 35_000;   // 1995-08-09
    private static final double DATE_SERIAL_MAX = 60_000;   // 2064-03-22

    private final UploadSessionRepository sessionRepo;
    private final SheetMetaRepository     sheetMetaRepo;
    private final CellDataRepository      cellDataRepo;

    @Transactional
    public String parse(MultipartFile file, List<String> selectedRules) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        log.info("[Parser] Starting parse for file={} session={}", file.getOriginalFilename(), sessionId);

        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.setIgnoreMissingWorkbooks(true);

            int sheetCount = wb.getNumberOfSheets();
            List<SheetMeta> metas = new ArrayList<>();
            List<CellData>  cells = new ArrayList<>();

            for (int si = 0; si < sheetCount; si++) {
                Sheet  sheet     = wb.getSheetAt(si);
                String sheetName = sheet.getSheetName();

                int maxRow = sheet.getLastRowNum();
                int maxCol = 0;
                for (Row row : sheet) {
                    if (row != null) maxCol = Math.max(maxCol, row.getLastCellNum());
                }

                metas.add(SheetMeta.builder()
                        .sessionId(sessionId).sheetName(sheetName)
                        .sheetIndex(si).rowCount(maxRow + 1).colCount(maxCol)
                        .build());

                for (Row row : sheet) {
                    if (row == null) continue;
                    boolean isFirstRow = (row.getRowNum() == sheet.getFirstRowNum());

                    for (int ci = 0; ci < maxCol; ci++) {
                        Cell cell = row.getCell(ci, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                        String formula = null;
                        String display;
                        String raw;
                        String type = cell.getCellType().name();

                        // Resolve FORMULA cells to their underlying type first
                        CellType effectiveType = cell.getCellType();
                        double   numericValue  = 0;

                        if (effectiveType == CellType.FORMULA) {
                            formula = "=" + cell.getCellFormula();
                            type    = "FORMULA";
                            try {
                                CellValue cv = evaluator.evaluate(cell);
                                effectiveType = (cv != null) ? cv.getCellType() : CellType.BLANK;
                                numericValue  = (effectiveType == CellType.NUMERIC)
                                                ? cv.getNumberValue() : 0;
                            } catch (Exception e) {
                                log.warn("[Parser] Formula eval failed row={} col={}: {}",
                                        row.getRowNum(), ci, e.getMessage());
                                display = cell.getCellFormula();
                                raw     = display;
                                cells.add(buildCell(sessionId, sheetName, row.getRowNum(),
                                        ci, display, raw, formula, type, isFirstRow));
                                continue;
                            }
                        } else if (effectiveType == CellType.NUMERIC) {
                            numericValue = cell.getNumericCellValue();
                        }

                        // ── DATE DETECTION (dual-gate) ────────────────────────────
                        // Gate A: POI says it's a date-formatted cell
                        // Gate B: numeric value falls in the plausible date serial range
                        // Either gate is sufficient to treat the cell as a date.
                        if (effectiveType == CellType.NUMERIC
                                && (DateUtil.isCellDateFormatted(cell)
                                    || isDateSerial(numericValue))) {
                            try {
                                LocalDate d = DateUtil
                                        .getLocalDateTime(numericValue, false)
                                        .toLocalDate();
                                display = formatDateDisplay(d);
                                raw     = d.format(ISO_FMT);
                                log.debug("[Parser] Date cell row={} col={} serial={} → {}",
                                        row.getRowNum(), ci, numericValue, raw);
                            } catch (Exception e) {
                                log.warn("[Parser] Date conversion failed row={} col={} val={}: {}",
                                        row.getRowNum(), ci, numericValue, e.getMessage());
                                display = new DataFormatter().formatCellValue(cell, evaluator);
                                raw     = display;
                            }

                        } else if (effectiveType == CellType.NUMERIC) {
                            // Plain numeric — format without date treatment
                            double d = numericValue;
                            display = (d == Math.floor(d) && !Double.isInfinite(d))
                                    ? String.valueOf((long) d)
                                    : String.valueOf(d);
                            raw = display;

                        } else {
                            // String, Boolean, Blank, Error — use DataFormatter
                            display = new DataFormatter().formatCellValue(cell, evaluator);
                            raw     = display;
                        }

                        cells.add(buildCell(sessionId, sheetName, row.getRowNum(),
                                ci, display, raw, formula, type, isFirstRow));
                    }
                }
                log.info("[Parser] Sheet='{}' rows={} cols={}", sheetName, maxRow + 1, maxCol);
            }

            sheetMetaRepo.saveAll(metas);
            for (int i = 0; i < cells.size(); i += 500) {
                cellDataRepo.saveAll(cells.subList(i, Math.min(i + 500, cells.size())));
            }

            String enabledRules = selectedRules == null
                    ? ""
                    : String.join(",", selectedRules);

            sessionRepo.save(
                    UploadSession.builder()
                            .sessionId(sessionId)
                            .fileName(file.getOriginalFilename())
                            .sheetCount(sheetCount)
                            .status("PARSED")
                            .enabledRules(enabledRules)
                            .build()
            );
        }

        log.info("[Parser] Complete session={}", sessionId);
        return sessionId;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CellData buildCell(String sessionId, String sheetName, int row, int col,
                               String display, String raw, String formula,
                               String type, boolean isHeader) {
        return CellData.builder()
                .sessionId(sessionId).sheetName(sheetName)
                .rowIdx(row).colIdx(col)
                .displayValue(truncate(display, 2000))
                .rawValue(truncate(raw, 2000))
                .formula(truncate(formula, 2000))
                .cellType(type)
                .isHeader(isHeader)
                .build();
    }

    /**
     * Returns true if value is a plausible Excel date serial.
     * 35000 = 1995-08-09, 60000 = 2064-03-22.
     * Excludes all typical non-date values in timesheets (hours, rates, counts).
     */
    private boolean isDateSerial(double value) {
        return value >= DATE_SERIAL_MIN && value <= DATE_SERIAL_MAX;
    }

    /** "01-Mar-26 (Sun)" — shown in the viewer and used as hover tooltip */
    private String formatDateDisplay(LocalDate d) {
        String day = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return d.format(DISPLAY_FMT) + " (" + day + ")";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
