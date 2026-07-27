package com.timesheet.validator.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single worksheet inside the Leave Planner workbook.
 *
 * Each worksheet contains:
 * - Sheet name
 * - Rows
 * - Cells within each row
 */
@Data
public class LeavePlannerSheet {

    /**
     * Original Excel worksheet name.
     *
     * Examples:
     * Jan'26
     * Feb'26
     * Count
     * Manage
     */
    private String sheetName;

    /**
     * Rows contained in the worksheet.
     *
     * Each inner List represents one Excel row.
     * Each String represents one cell value.
     */
    private List<List<String>> rows = new ArrayList<>();
}