package com.timesheet.validator.domain;

import lombok.*;
import javax.persistence.*;

/**
 * Runtime configuration for a single validation rule, stored in the database.
 *
 * <p>YAML ({@code app.rules}) provides the seed/defaults; this table is the
 * runtime source of truth. The {@code enabled} flag can be toggled in the DB
 * (or via the admin screen / H2 console) to switch a rule on or off without a
 * code change. The catalog service auto-reloads these rows on a schedule.</p>
 */
@Entity
@Table(name = "RULE_CONFIG")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable rule id used by the engine and the UI, e.g. "TS-01" / "PS-03". */
    @Column(name = "RULE_ID", unique = true, nullable = false)
    private String ruleId;

    /** Sheet this rule validates — drives the tab grouping. */
    @Column(name = "SHEET_NAME")
    private String sheetName;

    @Column(name = "DESCRIPTION", length = 300)
    private String description;

    /** CRITICAL | WARNING */
    @Column(name = "SEVERITY")
    private String severity;

    /** Global on/off. When false the rule is hidden from the UI and never runs. */
    @Column(name = "ENABLED")
    private Boolean enabled;

    /**
     * When true the rule is informational and not user-toggleable per upload
     * (e.g. the Pivot reconciliation checks, which always run if globally
     * enabled). When false the rule is selectable per upload.
     */
    @Column(name = "ALWAYS_ON")
    private Boolean alwaysOn;

    /** Display/order within its sheet group. */
    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    /**
     * Optional admin-editable message template shown when this rule is violated.
     * Placeholders: {detail} (engine-computed text), {ruleId}, {severity},
     * {field}. Blank/null = use the engine's default message.
     */
    @Column(name = "MESSAGE_TEMPLATE", length = 500)
    private String messageTemplate;
}
