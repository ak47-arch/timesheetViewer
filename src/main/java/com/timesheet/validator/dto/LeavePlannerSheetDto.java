package com.timesheet.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one complete worksheet from the Leave Planner workbook.
 *
 * This DTO contains:
 *
 * - Worksheet metadata
 * - Excel column widths
 * - Excel row heights
 * - Hidden rows/columns
 * - Merged regions
 * - Complete cell data and formatting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeavePlannerSheetDto {

    /**
     * Original Excel worksheet name.
     *
     * Examples:
     * Jan'26
     * Feb'26
     * Count
     * Manage
     * Incentive
     */
    private String sheetName;

    /**
     * Zero-based sheet index in the workbook.
     */
    private int sheetIndex;

    /**
     * Number of rows in the worksheet.
     */
    private int rowCount;

    /**
     * Number of columns in the worksheet.
     */
    private int colCount;

    /**
     * Excel column widths.
     *
     * Key   = zero-based column index
     * Value = width in pixels
     *
     * Example:
     *
     * 0 -> 200
     * 1 -> 120
     * 2 -> 100
     */
    @Builder.Default
    private List<Integer> columnWidths =
            new ArrayList<>();

    /**
     * Excel row heights.
     *
     * Key   = zero-based row index
     * Value = height in pixels
     */
    @Builder.Default
    private List<Integer> rowHeights =
            new ArrayList<>();

    /**
     * Hidden column flags.
     *
     * Index corresponds to the Excel column index.
     *
     * Example:
     *
     * hiddenColumns[2] = true
     *
     * means column C is hidden.
     */
    @Builder.Default
    private List<Boolean> hiddenColumns =
            new ArrayList<>();

    /**
     * Hidden row flags.
     *
     * Index corresponds to the Excel row index.
     */
    @Builder.Default
    private List<Boolean> hiddenRows =
            new ArrayList<>();

    /**
     * Merged cell regions in this worksheet.
     */
    @Builder.Default
    private List<LeavePlannerMergedRegionDto> mergedRegions =
            new ArrayList<>();

    /**
     * Complete worksheet cell grid.
     *
     * Outer list = rows
     *
     * Inner list = cells in each row
     */
    @Builder.Default
    private List<List<LeavePlannerCellDto>> rows =
            new ArrayList<>();
}