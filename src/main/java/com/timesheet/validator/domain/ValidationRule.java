package com.timesheet.validator.domain;

public enum ValidationRule {

    TS_01("TS-01", "Max 8 hrs/day per resource"),
    TS_02("TS-02", "No weekend entries"),
    TS_03("TS-03", "No public holiday entries"),
    TS_04("TS-04", "Hours must be positive"),
    TS_05("TS-05", "Resource must be in roster"),
    TS_06("TS-06", "SOW must match expected"),
    TS_07("TS-07", "Date within engagement period"),
    TS_08("TS-08", "All fields are mandatory"),

    //add
    TS_09("TS-09", "Pivot employee validation"),
    TS_10("TS-10", "Pivot hours validation"),
    TS_11("TS-11", "Pivot working days validation");


    private final String ruleId;
    private final String description;

    ValidationRule(String ruleId, String description) {
        this.ruleId = ruleId;
        this.description = description;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getDescription() {
        return description;
    }

    public static ValidationRule fromRuleId(String ruleId) {

        for (ValidationRule rule : values()) {

            if (rule.getRuleId().equals(ruleId)) {
                return rule;
            }
        }

        return null;
    }
}
