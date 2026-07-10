package com.timesheet.validator.model;

import java.util.Objects;

/**
 * Composite key representing a Project + Sub-Project combination.
 *
 * Used as a key for aggregation maps during Project-wise validation.
 */
public final class SubProjectKey {

    private final String projectName;
    private final String subProjectName;

    public SubProjectKey(String projectName,
                         String subProjectName) {
        this.projectName = normalize(projectName);
        this.subProjectName = normalize(subProjectName);
    }

    public String getProjectName() {
        return projectName;
    }

    public String getSubProjectName() {
        return subProjectName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SubProjectKey)) {
            return false;
        }

        SubProjectKey other = (SubProjectKey) obj;

        return Objects.equals(projectName, other.projectName)
                && Objects.equals(subProjectName, other.subProjectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, subProjectName);
    }

    @Override
    public String toString() {
        return projectName + " -> " + subProjectName;
    }

}