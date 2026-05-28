package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*;
@Entity @Table(name="SHEET_META") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SheetMeta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="SESSION_ID",nullable=false) private String sessionId;
    @Column(name="SHEET_NAME") private String sheetName;
    @Column(name="SHEET_INDEX") private Integer sheetIndex;
    @Column(name="ROW_COUNT") private Integer rowCount;
    @Column(name="COL_COUNT") private Integer colCount;
}
