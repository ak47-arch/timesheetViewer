package com.timesheet.validator.service;

import com.timesheet.validator.dto.LeavePlannerSheet;
import com.timesheet.validator.dto.LeavePlannerWorkbook;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for parsing Leave Planner Excel workbooks.
 *
 * This service performs generic workbook parsing only.
 *
 * It does not validate business rules or mandatory columns.
 * Validation is handled separately by LeavePlannerValidationService.
 *
 * Every worksheet is parsed, including support sheets such as:
 * - Count
 * - Manage
 *
 * This allows the complete workbook to be rendered on the UI.
 */
@Service
@Slf4j
public class LeavePlannerWorkbookParserService {

    /**
     * Parses the complete uploaded Leave Planner workbook.
     *
     * Every worksheet is read in its original order.
     *
     * @param file uploaded Leave Planner Excel workbook
     * @return parsed workbook representation
     * @throws IOException if the workbook cannot be read
     */
    public LeavePlannerWorkbook parse(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Leave Planner file cannot be empty."
            );
        }

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            LeavePlannerWorkbook parsedWorkbook =
                    new LeavePlannerWorkbook();

            /*
             * Iterate through every worksheet.
             *
             * IMPORTANT:
             * We intentionally do not skip Count or Manage here.
             *
             * ValidationService decides which sheets require validation.
             * ParserService reads every sheet because every sheet must
             * eventually be displayed on the UI.
             */
            for (Sheet sheet : workbook) {

                log.debug(
                        "Parsing Leave Planner sheet: {}",
                        sheet.getSheetName()
                );

                LeavePlannerSheet parsedSheet =
                        parseSheet(sheet);

                parsedWorkbook
                        .getSheets()
                        .add(parsedSheet);
            }

            log.info(
                    "Leave Planner workbook parsed successfully. " +
                            "Total sheets: {}",
                    parsedWorkbook.getSheets().size()
            );

            return parsedWorkbook;
        }
    }

    /**
     * Parses a single Excel worksheet.
     *
     * The method reads every row and every cell.
     *
     * Empty cells are preserved as empty strings so that
     * column positions remain consistent when rendered on the UI.
     *
     * @param sheet Excel worksheet
     * @return parsed worksheet
     */
    private LeavePlannerSheet parseSheet(Sheet sheet) {

        LeavePlannerSheet parsedSheet =
                new LeavePlannerSheet();

        parsedSheet.setSheetName(
                sheet.getSheetName()
        );

        DataFormatter formatter =
                new DataFormatter();

        /*
         * Iterate through all rows in the worksheet.
         */
        for (Row row : sheet) {

            List<String> parsedRow =
                    parseRow(row, formatter);

            parsedSheet
                    .getRows()
                    .add(parsedRow);
        }

        log.debug(
                "Sheet '{}' parsed successfully. Rows: {}",
                sheet.getSheetName(),
                parsedSheet.getRows().size()
        );

        return parsedSheet;
    }

    /**
     * Parses one Excel row.
     *
     * The row is converted into a List<String>.
     *
     * DataFormatter is used so that different Excel cell types
     * such as String, Numeric, Date, Boolean and Formula cells
     * are converted into their displayed Excel value.
     *
     * @param row Excel row
     * @param formatter Apache POI DataFormatter
     * @return list of cell values
     */
    private List<String> parseRow(
            Row row,
            DataFormatter formatter) {

        List<String> cells =
                new ArrayList<>();

        /*
         * Determine the last column used by this row.
         *
         * Using getLastCellNum() allows us to preserve
         * the actual column positions.
         */
        int lastCellNum =
                row.getLastCellNum();

        if (lastCellNum < 0) {
            return cells;
        }

        for (int columnIndex = 0;
             columnIndex < lastCellNum;
             columnIndex++) {

            Cell cell =
                    row.getCell(
                            columnIndex,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    );

            String value = "";

            if (cell != null) {

                value =
                        formatter
                                .formatCellValue(cell)
                                .trim();
            }

            cells.add(value);
        }

        return cells;
    }
}