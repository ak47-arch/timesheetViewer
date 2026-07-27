package com.timesheet.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedRegionDto {

    private int firstRow;

    private int lastRow;

    private int firstColumn;

    private int lastColumn;
}