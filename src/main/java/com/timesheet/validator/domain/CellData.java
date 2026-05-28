package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*;
@Entity @Table(name="CELL_DATA") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CellData {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="SESSION_ID",nullable=false) private String sessionId;
    @Column(name="SHEET_NAME") private String sheetName;
    @Column(name="ROW_IDX") private Integer rowIdx;
    @Column(name="COL_IDX") private Integer colIdx;
    @Column(name="DISPLAY_VALUE",length=2000) private String displayValue;
    @Column(name="RAW_VALUE",length=2000) private String rawValue;
    @Column(name="FORMULA",length=2000) private String formula;
    @Column(name="CELL_TYPE") private String cellType;
    @Column(name="IS_HEADER") private Boolean isHeader;
}
