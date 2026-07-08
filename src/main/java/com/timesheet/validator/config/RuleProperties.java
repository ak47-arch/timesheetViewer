package com.timesheet.validator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-driven defaults for the validation rule catalog ({@code app.rules}).
 *
 * <p>These definitions seed the {@code RULE_CONFIG} table on startup. After
 * seeding, the database is authoritative: an admin can enable/disable rules in
 * the DB and the catalog reloads them automatically (see {@code RuleCatalog}).
 * YAML governs the rule <i>structure</i> (which rules exist, their sheet,
 * description, severity, order, always-on); the DB governs the runtime
 * <i>enabled</i> state.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.rules")
@Data
public class RuleProperties {

    /** How often (ms) the catalog reloads rule config from the DB. */
    private long refreshMs = 60_000;

    /**
     * When true, startup re-seeds every rule's {@code enabled} flag from YAML,
     * overwriting any runtime changes. Default false so admin DB toggles
     * survive restarts. Rule metadata (sheet/description/severity/order/
     * always-on) is always synced from YAML regardless of this flag.
     */
    private boolean reseedOnStart = false;

    private List<RuleDefProps> definitions = new ArrayList<>();

    @Data
    public static class RuleDefProps {
        private String  ruleId;
        private String  sheet;
        private String  description;
        private String  severity   = "CRITICAL";
        private boolean enabled     = true;
        private boolean alwaysOn    = false;
        private int     sortOrder   = 0;
        /** Optional message template; blank = engine default. */
        private String  messageTemplate;
    }
}
