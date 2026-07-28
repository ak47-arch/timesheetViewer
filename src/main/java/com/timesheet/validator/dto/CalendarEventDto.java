package com.timesheet.validator.dto;

import lombok.*;

/**
 * One item in the calendar feed. Three kinds are merged into a single
 * flat list the front-end can render uniformly:
 *
 *  HOLIDAY    – public holiday (read-only, no leaveId)
 *  MY_LEAVE   – the signed-in user's own leave (editable → has leaveId)
 *  TEAM_LEAVE – another resource's leave (read-only)
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CalendarEventDto {

    public static final String KIND_HOLIDAY    = "HOLIDAY";
    public static final String KIND_MY_LEAVE   = "MY_LEAVE";
    public static final String KIND_TEAM_LEAVE = "TEAM_LEAVE";

    private String  kind;
    private String  date;      // ISO yyyy-MM-dd
    private String  title;     // holiday name or person's name
    private String  subtitle;  // leave type / reason / source
    private Long    leaveId;   // present only for MY_LEAVE (enables delete)
    private boolean editable;
    private String  source;    // USER_SELF | ADMIN_PLANNER | null (holiday)
}
