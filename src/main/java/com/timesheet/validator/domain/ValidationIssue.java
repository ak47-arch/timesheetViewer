package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*;
@Entity @Table(name="VALIDATION_ISSUE") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidationIssue {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="SESSION_ID",nullable=false) private String sessionId;
    @Column(name="RULE_ID") private String ruleId;
    @Column(name="SEVERITY") private String severity;
    @Column(name="SHEET_NAME") private String sheetName;
    @Column(name="ROW_IDX") private Integer rowIdx;
    @Column(name="COL_IDX") private Integer colIdx;
    @Column(name="FIELD_NAME") private String fieldName;
    @Column(name="MESSAGE",length=1000) private String message;
}
