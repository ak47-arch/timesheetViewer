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

    private final UploadSessionRepository sessionRepo;
    private final SheetMetaRepository sheetMetaRepo;
    private final CellDataRepository cellDataRepo;

    /**
     * Parses every sheet of the uploaded workbook, stores all cells (including
     * formulas) in H2, returns the sessionId for subsequent lookups.
     */
    @Transactional
    public String parse(MultipartFile file) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        log.info("[Parser] Starting parse for file={} session={}", file.getOriginalFilename(), sessionId);

        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.setIgnoreMissingWorkbooks(true);

            int sheetCount = wb.getNumberOfSheets();
            List<SheetMeta> metas = new ArrayList<>();
            List<CellData> cells = new ArrayList<>();

            for (int si = 0; si < sheetCount; si++) {
                Sheet sheet = wb.getSheetAt(si);
                String sheetName = sheet.getSheetName();

                int maxRow = sheet.getLastRowNum();
                int maxCol = 0;

                for (Row row : sheet) {
                    if (row == null) continue;
                    maxCol = Math.max(maxCol, row.getLastCellNum());
                }

                metas.add(SheetMeta.builder()
                    .sessionId(sessionId)
                    .sheetName(sheetName)
                    .sheetIndex(si)
                    .rowCount(maxRow + 1)
                    .colCount(maxCol)
                    .build());

                // Parse cells
                for (Row row : sheet) {
                    if (row == null) continue;
                    boolean isFirstRow = (row.getRowNum() == sheet.getFirstRowNum());

                    for (int ci = 0; ci < maxCol; ci++) {
                        Cell cell = row.getCell(ci, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String formula = null;
                        String display = "";
                        String raw = "";
                        String type = cell.getCellType().name();

                        if (cell.getCellType() == CellType.FORMULA) {
                            formula = "=" + cell.getCellFormula();
                            type = "FORMULA";
                            try {
                                CellValue cv = evaluator.evaluate(cell);
                                display = formatCellValue(cv);
                                raw = display;
                            } catch (Exception e) {
                                display = cell.getCellFormula();
                                raw = display;
                            }
                        } else {
                            display = getCellDisplayValue(cell, wb, evaluator);
                            raw = display;
                        }

                        cells.add(CellData.builder()
                            .sessionId(sessionId)
                            .sheetName(sheetName)
                            .rowIdx(row.getRowNum())
                            .colIdx(ci)
                            .displayValue(truncate(display, 2000))
                            .rawValue(truncate(raw, 2000))
                            .formula(truncate(formula, 2000))
                            .cellType(type)
                            .isHeader(isFirstRow)
                            .build());
                    }
                }
                log.info("[Parser] Sheet='{}' rows={} cols={}", sheetName, maxRow + 1, maxCol);
            }

            sheetMetaRepo.saveAll(metas);
            // Save in batches of 500 to avoid OOM
            for (int i = 0; i < cells.size(); i += 500) {
                cellDataRepo.saveAll(cells.subList(i, Math.min(i + 500, cells.size())));
            }

            UploadSession session = UploadSession.builder()
                .sessionId(sessionId)
                .fileName(file.getOriginalFilename())
                .sheetCount(sheetCount)
                .status("PARSED")
                .build();
            sessionRepo.save(session);
        }

        log.info("[Parser] Complete session={}", sessionId);
        return sessionId;
    }


    private String getCellDisplayValue(Cell cell, Workbook wb, FormulaEvaluator evaluator) {
        DataFormatter fmt = new DataFormatter();
        String formatted = fmt.formatCellValue(cell, evaluator);  // ← evaluator supplied

        // Fallback: if still a raw number for a date cell, convert manually
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            boolean isRawNumber = formatted.trim().matches("-?\\d+(\\.\\d+)?");
            if (isRawNumber || formatted.isBlank()) {
                LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                formatted = date.format(DateTimeFormatter.ofPattern("dd-MMM-yy")) + " (" + day + ")";
            }
        }
        return formatted;
    }
    private String formatCellValue(CellValue cv) {
        if (cv == null) return "";
        return switch (cv.getCellType()) {
            case NUMERIC -> {
                double d = cv.getNumberValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                    ? String.valueOf((long) d)
                    : String.valueOf(d);
            }
            case STRING  -> cv.getStringValue();
            case BOOLEAN -> String.valueOf(cv.getBooleanValue());
            case ERROR   -> "ERROR";
            default      -> "";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
