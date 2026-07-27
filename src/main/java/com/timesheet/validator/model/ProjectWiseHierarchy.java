package com.timesheet.validator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Returns the set of project names present in the hierarchy.
     */
    public Set<String> getProjectNames() {
        return projects.stream()
                .map(ProjectSummary::getProjectName)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of (project, subProject) keys present in the hierarchy.
     */
    public Set<SubProjectKey> getSubProjectKeys() {
        return subProjects.stream()
                .map(sp -> new SubProjectKey(sp.getProjectName(), sp.getSubProjectName()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of (project, subProject, projectCode) keys present in the hierarchy.
     */
    public Set<ProjectCodeKey> getProjectCodeKeys() {
        return projectCodes.stream()
                .map(pc -> new ProjectCodeKey(pc.getProjectName(), pc.getSubProjectName(), pc.getProjectCode()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of all sub-project names (regardless of parent project) in the hierarchy.
     */
    public Set<String> getSubProjectNames() {
        return subProjects.stream()
                .map(SubProjectSummary::getSubProjectName)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of all project code values (regardless of parent) in the hierarchy.
     */
    public Set<String> getProjectCodeValues() {
        return projectCodes.stream()
                .map(ProjectCodeSummary::getProjectCode)
                .collect(Collectors.toSet());
    }

    public boolean isEmpty() {
        return projects.isEmpty()
                && subProjects.isEmpty()
                && projectCodes.isEmpty();
    }

    /**
     * Returns true if the given project name exists in the hierarchy.
     */
    public boolean containsProject(String projectName) {
        return getProjectNames().contains(projectName);
    }

    /**
     * Returns true if the given (project, subProject) key exists in the hierarchy.
     */
    public boolean containsSubProject(String projectName, String subProjectName) {
        return getSubProjectKeys().contains(new SubProjectKey(projectName, subProjectName));
    }

    /**
     * Returns true if the given (project, subProject, projectCode) key exists in the hierarchy.
     */
    public boolean containsProjectCode(String projectName, String subProjectName, String projectCode) {
        return getProjectCodeKeys().contains(new ProjectCodeKey(projectName, subProjectName, projectCode));
    }

    /**
     * Returns the sub-project summaries for a given project name.
     */
    public List<SubProjectSummary> getSubProjectsForProject(String projectName) {
        return subProjects.stream()
                .filter(sp -> sp.getProjectName().equals(projectName))
                .collect(Collectors.toList());
    }

    /**
     * Returns the project code summaries for a given (project, subProject).
     */
    public List<ProjectCodeSummary> getProjectCodesForSubProject(String projectName, String subProjectName) {
        return projectCodes.stream()
                .filter(pc -> pc.getProjectName().equals(projectName) && pc.getSubProjectName().equals(subProjectName))
                .collect(Collectors.toList());
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