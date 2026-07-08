package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*; import java.math.BigDecimal; import java.time.LocalDate; import java.time.LocalDateTime;
@Entity @Table(name="TIMESHEET_ENTRY") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TimesheetEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="RESOURCE_NAME",nullable=false) private String resourceName;
    @Column(name="ENTRY_DATE",nullable=false) private LocalDate entryDate;
    @Column(name="ASSIGNED_TEAM") private String assignedTeam;
    @Column(name="PROJECT") private String project;
    @Column(name="SUB_PROJECT") private String subProject;
    @Column(name="PROJECT_CODE") private String projectCode;
    @Column(name="COUNTRY_CODE") private String countryCode;
    @Column(name="HOURS",nullable=false) private BigDecimal hours;
    @Column(name="TASK",length=1000) private String task;
    @Column(name="COMPANY") private String company;
    @Column(name="SOW") private String sow;
    @Column(name="SUBMITTED_BY") private String submittedBy;
    @Column(name="SUBMITTED_AT") private LocalDateTime submittedAt;
    @Column(name="STATUS") private String status;
    @Column(name="WEEKEND_OVERRIDE") private Boolean weekendOverride;
    @Column(name="WEEKEND_OVERRIDE_REASON",length=500) private String weekendOverrideReason;
    @PrePersist public void pre() { if(submittedAt==null) submittedAt=LocalDateTime.now(); if(status==null) status="SUBMITTED"; }
}
