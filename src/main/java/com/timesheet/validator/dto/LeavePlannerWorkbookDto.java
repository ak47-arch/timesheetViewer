package com.timesheet.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete uploaded Leave Planner Excel workbook.
 *
 * Every worksheet is preserved, including:
 *
 * - Monthly sheets
 * - Count
 * - Manage
 * - Incentive
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeavePlannerWorkbookDto {

    /**
     * All worksheets in their original Excel order.
     */
    @Builder.Default
    private List<LeavePlannerSheetDto> sheets =
            new ArrayList<>();
}
