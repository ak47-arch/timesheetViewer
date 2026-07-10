package com.timesheet.validator.service;

import com.timesheet.validator.domain.CellData;
import com.timesheet.validator.model.ProjectCodeSummary;
import com.timesheet.validator.model.ProjectSummary;
import com.timesheet.validator.model.ProjectWiseHierarchy;
import com.timesheet.validator.model.SubProjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses the Project-wise worksheet into an in-memory hierarchy.
 *
 * Responsibilities:
 *  - Parse Project totals (Table 1)
 *  - Parse Project/SubProject/PCode hierarchy (Table 2)
 *
 * Does NOT perform any validation.
 */
@Service
public class ProjectWiseParser {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProjectWiseParser.class);

    /*
     * Project-wise sheet layout.
     *
     * Table 1
     *
     * A = Project
     * B = Hours
     *
     * Table 2
     *
     * D = Project
     * E = Sub Project
     * F = Project Code
     * G = Hours
     */

    private static final int PROJECT_NAME_COL = 0;
    private static final int PROJECT_HOURS_COL = 1;

    private static final int HIERARCHY_PROJECT_COL = 3;
    private static final int SUB_PROJECT_COL = 4;
    private static final int PROJECT_CODE_COL = 5;
    private static final int HIERARCHY_HOURS_COL = 6;

    public ProjectWiseHierarchy parse(List<CellData> projectWiseCells) {

        LOGGER.info("Parsing Project-wise sheet.");

        ProjectWiseHierarchy hierarchy = new ProjectWiseHierarchy();

        if (projectWiseCells == null || projectWiseCells.isEmpty()) {
            return hierarchy;
        }

        Map<Integer, Map<Integer, CellData>> grid = buildGrid(projectWiseCells);

        parseProjectTotals(grid, hierarchy);

        parseHierarchy(grid, hierarchy);

        LOGGER.info(
                "Project-wise parsing completed. Projects={}, SubProjects={}, ProjectCodes={}",
                hierarchy.getProjects().size(),
                hierarchy.getSubProjects().size(),
                hierarchy.getProjectCodes().size());

        return hierarchy;
    }

    private void parseProjectTotals(
            Map<Integer, Map<Integer, CellData>> grid,
            ProjectWiseHierarchy hierarchy) {

        for (Integer row : grid.keySet()) {

            String project = value(grid, row, PROJECT_NAME_COL);

            String hours = value(grid, row, PROJECT_HOURS_COL);

            if (project.isBlank()
                    || hours.isBlank()
                    || "Grand Total".equalsIgnoreCase(project)
                    || "Row Labels".equalsIgnoreCase(project)) {
                continue;
            }

            hierarchy.addProject(
                    new ProjectSummary(
                            project,
                            parseHours(hours),
                            row));
        }
    }

    private void parseHierarchy(
            Map<Integer, Map<Integer, CellData>> grid,
            ProjectWiseHierarchy hierarchy) {

        String currentProject = null;
        String currentSubProject = null;

        for (Integer row : grid.keySet()) {

            String project = value(grid, row, HIERARCHY_PROJECT_COL);

            String subProject = value(grid, row, SUB_PROJECT_COL);

            String pCode = value(grid, row, PROJECT_CODE_COL);

            String hours = value(grid, row, HIERARCHY_HOURS_COL);

            if (!project.isBlank()) {
                currentProject = project;
            }

            if (!subProject.isBlank()) {
                currentSubProject = subProject;

                hierarchy.addSubProject(
                        new SubProjectSummary(
                                currentProject,
                                currentSubProject,
                                parseHours(hours),
                                row));
            }

            if (!pCode.isBlank()) {

                hierarchy.addProjectCode(
                        new ProjectCodeSummary(
                                currentProject,
                                currentSubProject,
                                pCode,
                                parseHours(hours),
                                row));
            }
        }
    }

    private Map<Integer, Map<Integer, CellData>> buildGrid(List<CellData> cells) {

        Map<Integer, Map<Integer, CellData>> grid = new TreeMap<>();

        for (CellData cell : cells) {

            grid.computeIfAbsent(
                    cell.getRowIdx(),
                    r -> new TreeMap<>())
                    .put(cell.getColIdx(), cell);
        }

        return grid;
    }

    private String value(
            Map<Integer, Map<Integer, CellData>> grid,
            int row,
            int col) {

        Map<Integer, CellData> rowData = grid.get(row);

        if (rowData == null) {
            return "";
        }

        CellData cell = rowData.get(col);

        if (cell == null || cell.getDisplayValue() == null) {
            return "";
        }

        return cell.getDisplayValue().trim();
    }

    private double parseHours(String value) {

        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (Exception ex) {
            return 0d;
        }
    }
}