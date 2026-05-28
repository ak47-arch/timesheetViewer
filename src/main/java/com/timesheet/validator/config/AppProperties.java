package com.timesheet.validator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * All app.master.* values from application.yml.
 * Edit YAML and restart — MasterDataLoader re-seeds H2 automatically.
 */
@Component
@ConfigurationProperties(prefix = "app.master")
@Data
public class AppProperties {

    private SowProps sow = new SowProps();
    private List<HolidayProps> holidays = new ArrayList<>();
    private List<ResourceProps> resources = new ArrayList<>();
    private ValidationProps validation = new ValidationProps();

    @Data
    public static class SowProps {
        private String sowNumber;
        private String poNumber;
        private BigDecimal poValue;
        private String client;
    }

    @Data
    public static class HolidayProps {
        /** yyyy-MM-dd as String — avoids LocalDate binding issues */
        private String holidayDate;
        private String holidayName;
        private String countryCode;
        private String notes;
    }

    @Data
    public static class ResourceProps {
        private String resourceId;
        private String name;
        private BigDecimal dailyRateUsd;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class ValidationProps {
        private double maxHoursPerDay = 8.0;
        private boolean allowWeekendOverride = false;
        private String expectedSow = "SOW_18_2026";
    }
}
