package com.timesheet.validator.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete Leave Planner Excel workbook.
 *
 * The workbook may contain multiple worksheets such as:
 * Jan'26, Feb'26, ..., Count and Manage.
 *
 * The original worksheet order is preserved.
 */
@Data
public class LeavePlannerWorkbook {

    private List<LeavePlannerSheet> sheets = new ArrayList<>();
}
