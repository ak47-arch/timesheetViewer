package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Composite key representing a Project + Sub-Project + Project Code
 * combination.
 *
 * Used as the key for Project Code aggregation during Phase 3 validation.
 */
public final class ProjectCodeKey {

    private final String projectName;
    private final String subProjectName;
    private final String projectCode;

    public ProjectCodeKey(String projectName,
                          String subProjectName,
                          String projectCode) {
        this.projectName = normalize(projectName);
        this.subProjectName = normalize(subProjectName);
        this.projectCode = normalize(projectCode);
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ProjectCodeKey)) {
            return false;
        }

        ProjectCodeKey other = (ProjectCodeKey) obj;

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
        return projectName
                + " -> "
                + subProjectName
                + " -> "
                + projectCode;
    }

}