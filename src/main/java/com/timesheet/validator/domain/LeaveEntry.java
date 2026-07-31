package com.timesheet.validator.domain;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single day of leave for one resource.
 *
 * A multi-day leave is stored as one row per calendar day so the
 * calendar feed and range queries stay trivial.
 *
 * {@code source} distinguishes leaves the user registered for
 * themselves ({@link #SOURCE_USER}) from leaves imported out of an
 * admin-uploaded Leave Planner workbook ({@link #SOURCE_ADMIN}).
 * Public holidays are NOT stored here — they live in
 * {@link PublicHoliday} and are overlaid on the calendar separately.
 */
@Entity
@Table(name = "LEAVE_ENTRY",
       uniqueConstraints = @UniqueConstraint(
           name = "UQ_LEAVE_RES_DATE_SRC",
           columnNames = {"RESOURCE_ID", "LEAVE_DATE", "SOURCE"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveEntry {

    public static final String SOURCE_USER  = "USER_SELF";
    public static final String SOURCE_ADMIN = "ADMIN_PLANNER";

    public static final String STATUS_APPROVED = "APPROVED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to RESOURCE.RESOURCE_ID (business key, not the surrogate id). */
    @Column(name = "RESOURCE_ID", nullable = false)
    private String resourceId;

    /** Denormalised for display + planner name-matching. */
    @Column(name = "RESOURCE_NAME")
    private String resourceName;

    @Column(name = "LEAVE_DATE", nullable = false)
    private LocalDate leaveDate;

    /** FULL_DAY / HALF_DAY, or the raw marker text carried over from a planner cell. */
    @Column(name = "LEAVE_TYPE")
    private String leaveType;

    /** Always APPROVED today (self-service). Kept for a future approval workflow. */
    @Column(name = "STATUS")
    private String status;

    /** USER_SELF | ADMIN_PLANNER */
    @Column(name = "SOURCE", nullable = false)
    private String source;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void pre() {
        if (status == null)    status = STATUS_APPROVED;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
