package com.timesheet.validator.service;

import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.model.ProjectCodeKey;
import com.timesheet.validator.model.ProjectKey;
import com.timesheet.validator.model.SubProjectKey;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates Timesheet data into lookup maps used by the
 * Project-wise validation engine.
 *
 * This service performs no validation. It only computes totals
 * grouped by Project, Sub-Project and Project Code.
 */
@Service
public class ProjectWiseAggregationService {

    private final Map<ProjectKey, Double> projectTotals =
            new HashMap<>();

    private final Map<SubProjectKey, Double> subProjectTotals =
            new HashMap<>();

    private final Map<ProjectCodeKey, Double> projectCodeTotals =
            new HashMap<>();

    /**
     * Builds all aggregation maps from the Timesheet sheet.
     */
    public void aggregate(List<CellData> timesheetCells) {

        projectTotals.clear();
        subProjectTotals.clear();
        projectCodeTotals.clear();

        /*
         * Implementation added next.
         *
         * We'll iterate over the parsed Timesheet rows,
         * extract:
         *
         * Project
         * Sub Project
         * Project Code
         * Hours
         *
         * and populate the maps.
         */
    }

    public double getProjectHours(String project) {

        return projectTotals.getOrDefault(
                new ProjectKey(project),
                0d);
    }

    public double getSubProjectHours(String project,
                                     String subProject) {

        return subProjectTotals.getOrDefault(
                new SubProjectKey(project, subProject),
                0d);
    }

    public double getProjectCodeHours(String project,
                                      String subProject,
                                      String projectCode) {

        return projectCodeTotals.getOrDefault(
                new ProjectCodeKey(project, subProject, projectCode),
                0d);
    }

}