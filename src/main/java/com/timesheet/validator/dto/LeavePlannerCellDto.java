package com.timesheet.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single cell from a Leave Planner Excel worksheet.
 *
 * This DTO contains both the cell's actual data and its Excel formatting
 * information so that the frontend can render the worksheet as close
 * as possible to the original Excel workbook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeavePlannerCellDto {

    /**
     * Zero-based row index in the Excel worksheet.
     */
    private int rowIdx;

    /**
     * Zero-based column index in the Excel worksheet.
     */
    private int colIdx;

    /**
     * Value displayed to the user.
     *
     * For formula cells, this will normally contain
     * the calculated/displayed result.
     */
    private String displayValue;

    /**
     * Original/raw cell value.
     */
    private String rawValue;

    /**
     * Excel formula, if this cell contains a formula.
     *
     * Example:
     * =SUM(B2:B10)
     */
    private String formula;

    /**
     * Excel cell type.
     *
     * Examples:
     * STRING
     * NUMERIC
     * FORMULA
     * BOOLEAN
     * BLANK
     */
    private String cellType;

    // ============================================================
    // Cell Formatting
    // ============================================================

    /**
     * Cell background/fill color in CSS-compatible format.
     *
     * Example:
     * #4472C4
     */
    private String backgroundColor;

    /**
     * Cell font color in CSS-compatible format.
     *
     * Example:
     * #FFFFFF
     */
    private String fontColor;

    /**
     * Whether the cell's font is bold.
     */
    private boolean bold;

    /**
     * Whether the cell's font is italic.
     */
    private boolean italic;

    /**
     * Excel font name.
     *
     * Example:
     * Calibri
     */
    private String fontName;

    /**
     * Font size in points.
     */
    private short fontSize;

    /**
     * Horizontal alignment.
     *
     * Examples:
     * LEFT
     * CENTER
     * RIGHT
     */
    private String horizontalAlignment;

    /**
     * Vertical alignment.
     *
     * Examples:
     * TOP
     * CENTER
     * BOTTOM
     */
    private String verticalAlignment;

    // ============================================================
    // Borders
    // ============================================================

    /**
     * CSS-compatible top border.
     *
     * Example:
     * 1px solid #000000
     */
    private String borderTop;

    /**
     * CSS-compatible bottom border.
     */
    private String borderBottom;

    /**
     * CSS-compatible left border.
     */
    private String borderLeft;

    /**
     * CSS-compatible right border.
     */
    private String borderRight;

    // ============================================================
    // Merge Information
    // ============================================================

    /**
     * Indicates whether this cell belongs to a merged Excel region.
     */
    private boolean merged;

    /**
     * Number of rows occupied by the merged region.
     *
     * For a normal cell this will be 1.
     */
    private int rowSpan;

    /**
     * Number of columns occupied by the merged region.
     *
     * For a normal cell this will be 1.
     */
    private int colSpan;
}