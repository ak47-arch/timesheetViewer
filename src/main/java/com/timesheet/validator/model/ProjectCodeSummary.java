package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Represents a Project Code (PCode) row extracted from the Project-wise sheet.
 *
 * Example:
 *
 * Project      : Australia Maintenance
 * Sub Project  : APP Mod AB-OPX
 * Project Code : 529NF.40.02
 * Hours        : 1226
 *
 * excelRow stores the original row number in the Project-wise sheet
 * and is used to generate ValidationIssue entries against the correct row.
 */
public final class ProjectCodeSummary {

    private final String projectName;
    private final String subProjectName;
    private final String projectCode;
    private final double hours;
    private final int excelRow;

    public ProjectCodeSummary(String projectName,
                              String subProjectName,
                              String projectCode,
                              double hours,
                              int excelRow) {
        this.projectName = projectName;
        this.subProjectName = subProjectName;
        this.projectCode = projectCode;
        this.hours = hours;
        this.excelRow = excelRow;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getSubProjectName() {
        return subProjectName;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public double getHours() {
        return hours;
    }

    public int getExcelRow() {
        return excelRow;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ProjectCodeSummary)) {
            return false;
        }

        ProjectCodeSummary other = (ProjectCodeSummary) obj;

        return Objects.equals(projectName, other.projectName)
                && Objects.equals(subProjectName, other.subProjectName)
                && Objects.equals(projectCode, other.projectCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, subProjectName, projectCode);
    }

    @Override
    public String toString() {
        return "ProjectCodeSummary{" +
                "projectName='" + projectName + '\'' +
                ", subProjectName='" + subProjectName + '\'' +
                ", projectCode='" + projectCode + '\'' +
                ", hours=" + hours +
                ", excelRow=" + excelRow +
                '}';
    }
}