package com.timesheet.validator.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class LeavePlannerWorkbookService {

    /**
     * Parses the complete Leave Planner workbook.
     *
     * Every worksheet is parsed dynamically.
     *
     * The parser preserves:
     *
     * - Cell values
     * - Formulas
     * - Cell types
     * - Background colors
     * - Font colors
     * - Font size
     * - Bold / italic
     * - Borders
     * - Alignment
     * - Column widths
     * - Row heights
     * - Merged regions
     */
    public LeavePlannerWorkbook parseWorkbook(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Leave Planner file cannot be empty."
            );
        }

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            LeavePlannerWorkbook result =
                    new LeavePlannerWorkbook();

            for (int sheetIndex = 0;
                 sheetIndex < workbook.getNumberOfSheets();
                 sheetIndex++) {

                Sheet sheet =
                        workbook.getSheetAt(sheetIndex);

                log.debug(
                        "Parsing Leave Planner worksheet: {}",
                        sheet.getSheetName()
                );

                LeavePlannerSheet parsedSheet =
                        parseSheet(
                                workbook,
                                sheet,
                                sheetIndex
                        );

                result.getSheets().add(parsedSheet);
            }

            log.info(
                    "Leave Planner workbook parsed successfully. " +
                            "Total worksheets: {}",
                    result.getSheets().size()
            );

            return result;
        }
    }


    /**
     * Parses one worksheet.
     */
    private LeavePlannerSheet parseSheet(
            Workbook workbook,
            Sheet sheet,
            int sheetIndex) {

        LeavePlannerSheet result =
                new LeavePlannerSheet();

        result.setSheetName(
                sheet.getSheetName()
        );

        result.setSheetIndex(
                sheetIndex
        );


        DataFormatter formatter =
                new DataFormatter();

        FormulaEvaluator evaluator =
                workbook
                        .getCreationHelper()
                        .createFormulaEvaluator();


        /*
         * Determine maximum number of columns.
         */
        int maxColumns = 0;

        for (Row row : sheet) {

            if (row != null &&
                    row.getLastCellNum() > maxColumns) {

                maxColumns =
                        row.getLastCellNum();
            }
        }


        /*
         * Parse rows and cells.
         */
        for (Row row : sheet) {

            List<LeavePlannerCell> parsedRow =
                    new ArrayList<>();

            for (int columnIndex = 0;
                 columnIndex < maxColumns;
                 columnIndex++) {

                Cell cell =
                        row.getCell(
                                columnIndex,
                                Row.MissingCellPolicy
                                        .RETURN_BLANK_AS_NULL
                        );

                LeavePlannerCell parsedCell =
                        parseCell(
                                workbook,
                                cell,
                                formatter,
                                evaluator
                        );

                parsedRow.add(
                        parsedCell
                );
            }

            result.getRows().add(
                    parsedRow
            );
        }


        /*
         * Store column widths.
         */
        List<Integer> columnWidths =
                new ArrayList<>();

        for (int columnIndex = 0;
             columnIndex < maxColumns;
             columnIndex++) {

            columnWidths.add(
                    sheet.getColumnWidth(
                            columnIndex
                    )
            );
        }

        result.setColumnWidths(
                columnWidths
        );


        /*
         * Store row heights.
         */
        List<Short> rowHeights =
                new ArrayList<>();

        for (int rowIndex = 0;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row =
                    sheet.getRow(
                            rowIndex
                    );

            if (row != null) {

                rowHeights.add(
                        row.getHeight()
                );

            } else {

                rowHeights.add(
                        sheet.getDefaultRowHeight()
                );
            }
        }

        result.setRowHeights(
                rowHeights
        );


        /*
         * Store merged cell regions.
         *
         * Example:
         *
         * A1:C1
         */
        List<LeavePlannerMergedRegion>
                mergedRegions =
                new ArrayList<>();

        for (int i = 0;
             i < sheet.getNumMergedRegions();
             i++) {

            var region =
                    sheet.getMergedRegion(i);

            LeavePlannerMergedRegion mergedRegion =
                    new LeavePlannerMergedRegion();

            mergedRegion.setFirstRow(
                    region.getFirstRow()
            );

            mergedRegion.setLastRow(
                    region.getLastRow()
            );

            mergedRegion.setFirstColumn(
                    region.getFirstColumn()
            );

            mergedRegion.setLastColumn(
                    region.getLastColumn()
            );

            mergedRegions.add(
                    mergedRegion
            );
        }

        result.setMergedRegions(
                mergedRegions
        );


        return result;
    }


    /**
     * Parses an individual Excel cell.
     */
    private LeavePlannerCell parseCell(
            Workbook workbook,
            Cell cell,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        LeavePlannerCell result =
                new LeavePlannerCell();


        /*
         * Blank cell.
         */
        if (cell == null) {

            result.setValue("");

            return result;
        }


        /*
         * Cell type.
         */
        result.setCellType(
                cell.getCellType().name()
        );


        /*
         * Display value.
         */
        result.setValue(
                formatter.formatCellValue(
                        cell,
                        evaluator
                )
        );


        /*
         * Formula.
         */
        if (cell.getCellType() ==
                CellType.FORMULA) {

            result.setFormula(
                    "=" + cell.getCellFormula()
            );
        }


        /*
         * Background color.
         */
//        result.setBackgroundColor(
//                getColor(
//                        cell
//                                .getCellStyle()
//                                .getFillForegroundColorColor()
//                )
//        );

        CellStyle style = cell.getCellStyle();

        if (style.getFillPattern() != FillPatternType.NO_FILL) {

            result.setBackgroundColor(
                    getColor(
                            style.getFillForegroundColorColor()
                    )
            );
        }


        /*
         * Font.
         */
        Font font =
                workbook.getFontAt(
                        cell
                                .getCellStyle()
                                .getFontIndex()
                );

        result.setBold(
                font.getBold()
        );

        result.setItalic(
                font.getItalic()
        );

        result.setFontSize(
                font.getFontHeightInPoints()
        );

        if (font instanceof org.apache.poi.xssf.usermodel.XSSFFont) {

            org.apache.poi.xssf.usermodel.XSSFFont xssfFont =
                    (org.apache.poi.xssf.usermodel.XSSFFont) font;

            result.setFontColor(
                    getColor(
                            xssfFont.getXSSFColor()
                    )
            );
        }


        /*
         * Alignment.
         */
        result.setHorizontalAlignment(
                cell
                        .getCellStyle()
                        .getAlignment()
                        .name()
        );

        result.setVerticalAlignment(
                cell
                        .getCellStyle()
                        .getVerticalAlignment()
                        .name()
        );


        /*
         * Borders.
         */
        result.setBorderTop(
                cell
                        .getCellStyle()
                        .getBorderTop()
                        .name()
        );

        result.setBorderBottom(
                cell
                        .getCellStyle()
                        .getBorderBottom()
                        .name()
        );

        result.setBorderLeft(
                cell
                        .getCellStyle()
                        .getBorderLeft()
                        .name()
        );

        result.setBorderRight(
                cell
                        .getCellStyle()
                        .getBorderRight()
                        .name()
        );


        return result;
    }


    /**
     * Converts POI color information to a CSS-compatible
     * hexadecimal color.
     */
    private String getColor(
            org.apache.poi.ss.usermodel.Color color) {

        if (color instanceof
                org.apache.poi.xssf.usermodel.XSSFColor) {

            byte[] rgb =
                    ((org.apache.poi.xssf.usermodel.XSSFColor)
                            color)
                            .getRGB();

            if (rgb != null &&
                    rgb.length >= 3) {

                return String.format(
                        "#%02X%02X%02X",
                        rgb[0] & 0xFF,
                        rgb[1] & 0xFF,
                        rgb[2] & 0xFF
                );
            }
        }

        return null;
    }

//    private String getColor(XSSFColor color) {
//
//        if (color == null) {
//            return null;
//        }
//
//        byte[] rgb = color.getRGB();
//
//        if (rgb != null && rgb.length >= 3) {
//            return String.format(
//                    "#%02X%02X%02X",
//                    rgb[0] & 0xFF,
//                    rgb[1] & 0xFF,
//                    rgb[2] & 0xFF
//            );
//        }
//
//        return null;
//    }


    // ================================================================
    // WORKBOOK MODEL
    // ================================================================

    @Data
    public static class LeavePlannerWorkbook {

        private List<LeavePlannerSheet> sheets =
                new ArrayList<>();
    }


    // ================================================================
    // SHEET MODEL
    // ================================================================

    @Data
    public static class LeavePlannerSheet {

        private String sheetName;

        private int sheetIndex;

        private List<List<LeavePlannerCell>> rows =
                new ArrayList<>();

        private List<Integer> columnWidths =
                new ArrayList<>();

        private List<Short> rowHeights =
                new ArrayList<>();

        private List<LeavePlannerMergedRegion>
                mergedRegions =
                new ArrayList<>();
    }


    // ================================================================
    // CELL MODEL
    // ================================================================

    @Data
    public static class LeavePlannerCell {

        private String value;

        private String formula;

        private String cellType;

        private String backgroundColor;

        private String fontColor;

        private short fontSize;

        private boolean bold;

        private boolean italic;

        private String horizontalAlignment;

        private String verticalAlignment;

        private String borderTop;

        private String borderBottom;

        private String borderLeft;

        private String borderRight;
    }


    // ================================================================
    // MERGED REGION MODEL
    // ================================================================

    @Data
    public static class LeavePlannerMergedRegion {

        private int firstRow;

        private int lastRow;

        private int firstColumn;

        private int lastColumn;
    }
}