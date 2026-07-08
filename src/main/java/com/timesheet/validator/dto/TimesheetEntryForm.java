package com.timesheet.validator.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Form DTO for the timesheet entry page.
 *
 * entryDate is kept as String (yyyy-MM-dd) to avoid Spring MVC's
 * LocalDate binding issue with HTML date inputs. It is converted
 * to LocalDate explicitly in TimesheetController via @InitBinder.
 */
@Data
public class TimesheetEntryForm {

    // String — HTML date inputs send "yyyy-MM-dd"; @InitBinder converts
    @NotBlank(message = "Date is required")
    private String entryDate;

    @NotBlank(message = "Project is required")
    private String project;

    private String subProject;
    private String projectCode;

    @NotNull(message = "Hours are required")
    @DecimalMin(value = "0.5", message = "Hours must be at least 0.5")
    private BigDecimal hours;

    private String assignedTeam;
    private String task;
    private String company;

    @NotBlank(message = "SOW is required — please select from the dropdown")
    private String sow;

    private String countryCode;

    // Weekend override fields
    private boolean weekendOverride;        // checkbox
    private String weekendOverrideReason;   // selected or typed reason
}
