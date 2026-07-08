package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Represents a Sub-Project row extracted from the Project-wise sheet.
 *
 * Example:
 *
 * Project      : Australia Maintenance
 * Sub Project  : APP Mod AB-OPX
 * Hours        : 1226
 *
 * excelRow stores the original row number in the Project-wise sheet
 * and is used to generate ValidationIssue entries against the correct row.
 */
public final class SubProjectSummary {

    private final String projectName;
    private final String subProjectName;
    private final double hours;
    private final int excelRow;

    public SubProjectSummary(String projectName,
                             String subProjectName,
                             double hours,
                             int excelRow) {
        this.projectName = projectName;
        this.subProjectName = subProjectName;
        this.hours = hours;
        this.excelRow = excelRow;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getSubProjectName() {
        return subProjectName;
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

        if (!(obj instanceof SubProjectSummary)) {
            return false;
        }

        SubProjectSummary other = (SubProjectSummary) obj;

        return Objects.equals(projectName, other.projectName)
                && Objects.equals(subProjectName, other.subProjectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, subProjectName);
    }

    @Override
    public String toString() {
        return "SubProjectSummary{" +
                "projectName='" + projectName + '\'' +
                ", subProjectName='" + subProjectName + '\'' +
                ", hours=" + hours +
                ", excelRow=" + excelRow +
                '}';
    }
}