package com.timesheet.validator.dto;

import lombok.*;
import java.util.List;

/** One sheet's full data ready for Thymeleaf rendering */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SheetDto {
    private String sheetName;
    private int sheetIndex;
    private int rowCount;
    private int colCount;
    /** rows[rowIdx][colIdx] */
    private List<List<CellDto>> rows;
}
