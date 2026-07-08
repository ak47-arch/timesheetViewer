package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Represents a Project row extracted from the Project-wise sheet.
 *
 * Example:
 *
 * Project : Australia Maintenance
 * Hours   : 2228
 *
 * excelRow stores the original row number in the Project-wise sheet
 * and is used to generate ValidationIssue entries against the correct row.
 */
public final class ProjectSummary {

    private final String projectName;
    private final double hours;
    private final int excelRow;

    public ProjectSummary(String projectName,
                          double hours,
                          int excelRow) {
        this.projectName = projectName;
        this.hours = hours;
        this.excelRow = excelRow;
    }

    public String getProjectName() {
        return projectName;
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

        if (!(obj instanceof ProjectSummary)) {
            return false;
        }

        ProjectSummary other = (ProjectSummary) obj;

        return Objects.equals(projectName, other.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName);
    }

    @Override
    public String toString() {
        return "ProjectSummary{" +
                "projectName='" + projectName + '\'' +
                ", hours=" + hours +
                ", excelRow=" + excelRow +
                '}';
    }

}