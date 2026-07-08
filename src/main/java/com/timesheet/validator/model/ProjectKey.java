package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Composite key representing a Project.
 *
 * Used as a key for aggregation maps.
 */
public final class ProjectKey {

    private final String projectName;

    public ProjectKey(String projectName) {
        this.projectName = normalize(projectName);
    }

    public String getProjectName() {
        return projectName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ProjectKey)) {
            return false;
        }

        ProjectKey other = (ProjectKey) obj;

        return Objects.equals(projectName, other.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName);
    }

    @Override
    public String toString() {
        return projectName;
    }

}