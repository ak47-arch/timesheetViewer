package com.timesheet.validator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the parsed contents of the Project-wise worksheet.
 *
 * The worksheet contains two logical data sets:
 *
 * 1. Project totals
 * 2. Project -> Sub Project -> Project Code hierarchy
 *
 * This object is produced by ProjectWiseParser and consumed by the
 * Project-wise validation services.
 */
public class ProjectWiseHierarchy {

    private final List<ProjectSummary> projects = new ArrayList<>();

    private final List<SubProjectSummary> subProjects = new ArrayList<>();

    private final List<ProjectCodeSummary> projectCodes = new ArrayList<>();

    public void addProject(ProjectSummary project) {
        projects.add(project);
    }

    public void addSubProject(SubProjectSummary subProject) {
        subProjects.add(subProject);
    }

    public void addProjectCode(ProjectCodeSummary projectCode) {
        projectCodes.add(projectCode);
    }

    public List<ProjectSummary> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    public List<SubProjectSummary> getSubProjects() {
        return Collections.unmodifiableList(subProjects);
    }

    public List<ProjectCodeSummary> getProjectCodes() {
        return Collections.unmodifiableList(projectCodes);
    }

    public boolean isEmpty() {
        return projects.isEmpty()
                && subProjects.isEmpty()
                && projectCodes.isEmpty();
    }

    @Override
    public String toString() {
        return "ProjectWiseHierarchy{" +
                "projects=" + projects.size() +
                ", subProjects=" + subProjects.size() +
                ", projectCodes=" + projectCodes.size() +
                '}';
    }
}