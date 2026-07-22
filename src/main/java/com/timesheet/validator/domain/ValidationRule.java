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
    PS_01("PS-01", "Pivot employee validation"),
    PS_02("PS-02", "Pivot hours validation"),
    PS_03("PS-03", "Pivot date totals validation"),
    PS_04("PS-04", "Pivot employee/date validation"),
    PS_05("PS-05", "Pivot grand total validation"),
    PS_06("PS-06", "Pivot working days validation"),
    PW_001("PW-001", "Project totals validation"),
    PW_002("PW-002", "Sub Project totals validation"),
    PW_003("PW-003", "Project Code totals validation"),

    SM_01("SM-01", "SOW No + Description cross-ref against SOW_MASTER"),
    SM_02("SM-02", "Employee Name cross-ref against RESOURCE + RESOURCE_SOW"),
    SM_03("SM-03", "Daily Rate cross-ref against RESOURCE.DAILY_RATE_USD"),
    SM_04("SM-04", "Billing Period cross-ref against RESOURCE.START_DATE / END_DATE"),
    SM_05("SM-05", "PO Number cross-ref against SOW_MASTER.PO_NUMBER"),
    SM_07("SM-07", "Working Days three-way reconciliation (Timesheet/Pivot/Summary)"),
    SM_08("SM-08", "Travel Expense sanity check"),
    SM_09("SM-09", "Total Amount formula validation"),

    CM_01("CM-01", "Project Information validation (Name + ID)"),
    CM_02("CM-02", "PO Validation & Resource Count Validation"),
    CM_03("CM-03", "Total Billable Days Validation"),
    CM_04("CM-04", "Total Billable Amount Validation"),
    CM_05("CM-05", "Planned Value, Actual Value & PO Balance Validation"),
    CM_06("CM-06", "Positive PO Balance Validation");


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
