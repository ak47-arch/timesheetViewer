package com.timesheet.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a merged cell region from an Excel worksheet.
 *
 * Example:
 *
 * If Excel contains a merged region:
 *
 * A1:D1
 *
 * then:
 *
 * firstRow    = 0
 * lastRow     = 0
 * firstColumn = 0
 * lastColumn  = 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeavePlannerMergedRegionDto {

    /**
     * First row of the merged region.
     */
    private int firstRow;

    /**
     * Last row of the merged region.
     */
    private int lastRow;

    /**
     * First column of the merged region.
     */
    private int firstColumn;

    /**
     * Last column of the merged region.
     */
    private int lastColumn;
}