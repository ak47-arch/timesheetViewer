package com.timesheet.validator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LeavePlannerValidationService {

    private static final String EMPLOYEE_NAME_HEADER = "employee name";
    private static final String NAME_DAY_HEADER = "name/day";
    private static final String TEAM_HEADER = "team";
    private static final Pattern MONTH_SHEET_PATTERN =
            Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)'\\d{2}$");

    /**
     * Validates the structure of an uploaded Leave Planner Excel file.
     *
     * The header row is detected dynamically instead of relying on
     * a fixed row number. This allows the template to contain additional
     * rows above the actual header in future versions.
     *
     * @param file uploaded Leave Planner Excel file
     * @return true when the file contains the mandatory headers
     */
    /**
     * Validates the structure of an uploaded Leave Planner Excel workbook.
     *
     * Every worksheet in the workbook is validated independently.
     * Each worksheet must contain a dynamically detected header row
     * with the mandatory Employee Name/Name-Day and Team columns.
     *
     * @param file uploaded Leave Planner Excel file
     * @return true when every worksheet has the required structure
     */
    public boolean validateTemplate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            log.warn("Leave Planner upload is empty.");
            return false;
        }

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            if (workbook.getNumberOfSheets() == 0) {
                log.warn("Leave Planner workbook does not contain any sheets.");
                return false;
            }

            /*
             * Validate every worksheet in the uploaded workbook.
             *
             * A Leave Planner workbook may contain multiple monthly sheets,
             * for example:
             * Jan'26, Feb'26, Mar'26, etc.
             *
             * Each sheet must independently contain the mandatory
             * Employee Name/Name-Day and Team headers.
             */
            for (Sheet sheet : workbook) {

                String sheetName = sheet.getSheetName();

                /*
                 * The Leave Planner workbook contains monthly data sheets
                 * such as Jan'26, Feb'26, Mar'26, etc.
                 *
                 * Supporting sheets such as Count and Manage are not
                 * Leave Planner data sheets and therefore do not require
                 * Employee Name/Name-Day and Team headers.
                 */
                if (!isMonthlyLeavePlannerSheet(sheetName)) {

                    log.debug(
                            "Skipping non-monthly Leave Planner support sheet: {}",
                            sheetName
                    );

                    continue;
                }

                log.debug(
                        "Validating Leave Planner sheet: {}",
                        sheetName
                );

                Row headerRow = findHeaderRow(sheet);

                if (headerRow == null) {

                    log.warn(
                            "Header row could not be found in Leave Planner sheet '{}'. " +
                                    "Mandatory headers: Employee Name/Name-Day and Team.",
                            sheetName
                    );

                    return false;
                }

                Map<String, Integer> headers =
                        getHeaderIndexes(headerRow);

                boolean hasEmployee =
                        headers.containsKey(EMPLOYEE_NAME_HEADER)
                                || headers.containsKey(NAME_DAY_HEADER);

                boolean hasTeam =
                        headers.containsKey(TEAM_HEADER);

                if (!hasEmployee || !hasTeam) {

                    log.warn(
                            "Invalid Leave Planner format in sheet '{}'. " +
                                    "Employee Name/Name-Day and Team columns are mandatory.",
                            sheetName
                    );

                    return false;
                }

                log.debug(
                        "Leave Planner sheet '{}' validated successfully. " +
                                "Header row found at index {}.",
                        sheetName,
                        headerRow.getRowNum()
                );
            }

            /*
             * Validation succeeds only when every sheet
             * in the workbook passes validation.
             */
            log.info(
                    "Leave Planner workbook validation successful. " +
                            "Validated {} sheet(s).",
                    workbook.getNumberOfSheets()
            );

            return true;

        } catch (IOException e) {

            log.error(
                    "Unable to read uploaded Leave Planner Excel file.",
                    e
            );

            return false;

        } catch (Exception e) {

            log.error(
                    "Invalid Leave Planner Excel file.",
                    e
            );

            return false;
        }
    }


    /**
     * Determines whether the worksheet represents a monthly
     * Leave Planner data sheet.
     *
     * Examples of valid monthly sheet names:
     * Jan'26
     * Feb'26
     * Mar'26
     *
     * Supporting sheets such as Count and Manage are ignored
     * during Leave Planner data-sheet validation.
     *
     * @param sheetName Excel worksheet name
     * @return true when the sheet represents a monthly Leave Planner sheet
     */
    private boolean isMonthlyLeavePlannerSheet(String sheetName) {

        if (sheetName == null || sheetName.isBlank()) {
            return false;
        }

        return MONTH_SHEET_PATTERN
                .matcher(sheetName.trim())
                .matches();
    }

    /**
     * Dynamically searches the worksheet for the header row.
     *
     * The method does not assume that the header is always at row 2
     * or any other fixed position.
     *
     * A valid header row must contain:
     * - Employee Name OR Name/Day
     * - Team
     *
     * @param sheet Excel worksheet
     * @return detected header row, or null if not found
     */
    private Row findHeaderRow(Sheet sheet) {

        for (Row row : sheet) {

            boolean hasEmployeeName = false;
            boolean hasTeam = false;

            for (Cell cell : row) {

                String value = getCellValue(cell);

                if (value.isBlank()) {
                    continue;
                }

                String normalizedValue =
                        normalizeHeader(value);

                if (EMPLOYEE_NAME_HEADER.equals(normalizedValue)
                        || NAME_DAY_HEADER.equals(normalizedValue)) {

                    hasEmployeeName = true;
                }

                if (TEAM_HEADER.equals(normalizedValue)) {

                    hasTeam = true;
                }
            }

            if (hasEmployeeName && hasTeam) {
                return row;
            }
        }

        return null;
    }

    /**
     * Creates a map containing normalized header names and their
     * corresponding Excel column indexes.
     *
     * Example:
     *
     * "Name/Day" -> 0
     * "Team"     -> 1
     * "1-Jul-26" -> 2
     *
     * @param headerRow detected header row
     * @return header-to-column-index map
     */
    private Map<String, Integer> getHeaderIndexes(Row headerRow) {

        Map<String, Integer> indexes = new HashMap<>();

        for (Cell cell : headerRow) {

            String headerValue = getCellValue(cell);

            if (headerValue.isBlank()) {
                continue;
            }

            indexes.put(
                    normalizeHeader(headerValue),
                    cell.getColumnIndex()
            );
        }

        return indexes;
    }

    /**
     * Normalizes header values so that validation is not affected
     * by differences in capitalization or accidental spaces.
     */
    private String normalizeHeader(String value) {

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    /**
     * Safely reads the displayed value of an Excel cell.
     *
     * DataFormatter is used instead of getStringCellValue() because
     * Excel cells may contain String, Numeric, Boolean, Formula, etc.
     */
    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter = new DataFormatter();

        return formatter
                .formatCellValue(cell)
                .trim();
    }
}