package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*; import java.time.LocalDate;
@Entity @Table(name="PUBLIC_HOLIDAY") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicHoliday {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="HOLIDAY_DATE",nullable=false) private LocalDate holidayDate;
    @Column(name="HOLIDAY_NAME") private String holidayName;
    @Column(name="COUNTRY_CODE") private String countryCode;
    @Column(name="NOTES") private String notes;
    @Column(name="ENABLED") private Boolean enabled;

    /** Null-safe: a holiday with no explicit flag is treated as enabled. */
    public boolean isActive() { return enabled == null || enabled; }

    @PrePersist public void pre() { if (enabled == null) enabled = Boolean.TRUE; }
}
