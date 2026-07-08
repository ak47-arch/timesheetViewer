package com.timesheet.validator.config;

import com.timesheet.validator.domain.RuleConfig;
import com.timesheet.validator.repository.RuleConfigRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed catalog of validation rules, grouped by sheet.
 *
 * <p>Rule definitions are seeded from YAML ({@code app.rules}) into the
 * {@code RULE_CONFIG} table on startup, after which the database is the source
 * of truth. The catalog keeps an in-memory snapshot that is refreshed:</p>
 * <ul>
 *   <li>once on application ready (after the seeder has run),</li>
 *   <li>automatically on a schedule ({@code app.rules.refresh-ms}), so DB edits
 *       (including direct H2-console updates) are picked up without a restart,</li>
 *   <li>immediately whenever a rule is toggled through {@link #setEnabled}.</li>
 * </ul>
 *
 * <p>The public API ({@link #getGroups()}, {@link #getToggleableRuleIds()},
 * {@link #getToggleableRuleCount()}) is unchanged from the previous static
 * version, so the controllers and templates need no changes. New methods expose
 * the global enable/disable state used to gate the engine.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleCatalog {

    @Getter
    @AllArgsConstructor
    public static class RuleDef {
        private final String id;
        private final String description;
        private final String severity;
    }

    @Getter
    @AllArgsConstructor
    public static class RuleGroup {
        private final String sheetName;
        private final boolean alwaysOn;
        private final List<RuleDef> rules;
    }

    private final RuleConfigRepository repo;

    /** Only enabled rules, grouped by sheet — drives the UI. */
    private volatile List<RuleGroup> groups = new ArrayList<>();

    /** Every rule id -> enabled flag (incl. disabled) — gates the engine. */
    private final Map<String, Boolean> enabledById = new ConcurrentHashMap<>();

    /** Every rule id -> configured message template (blank if default). */
    private final Map<String, String> messageById = new ConcurrentHashMap<>();

    // ── Lifecycle / refresh ──────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.rules.refresh-ms:60000}")
    public void scheduledRefresh() {
        refresh();
    }

    /** Reload the in-memory snapshot from the database. */
    public synchronized void refresh() {
        List<RuleConfig> all = repo.findAllByOrderBySortOrderAscIdAsc();

        // Track enabled state + message template for ALL rules.
        enabledById.clear();
        messageById.clear();
        for (RuleConfig rc : all) {
            // Null or true => enabled. Only an explicit false disables a rule,
            // so a rule can never be silently suppressed by a missing flag.
            enabledById.put(rc.getRuleId(), rc.getEnabled() == null || rc.getEnabled());
            if (rc.getMessageTemplate() != null && !rc.getMessageTemplate().isBlank()) {
                messageById.put(rc.getRuleId(), rc.getMessageTemplate());
            }
        }

        // Build groups from ENABLED rules only, preserving sheet order.
        Map<String, List<RuleDef>>  rulesBySheet = new LinkedHashMap<>();
        Map<String, Boolean>        sheetAlwaysOn = new LinkedHashMap<>();
        for (RuleConfig rc : all) {
            if (!Boolean.TRUE.equals(rc.getEnabled())) continue;
            rulesBySheet.computeIfAbsent(rc.getSheetName(), k -> new ArrayList<>())
                    .add(new RuleDef(rc.getRuleId(), rc.getDescription(), rc.getSeverity()));
            // A group is "always on" only if every enabled rule in it is always-on.
            boolean ao = Boolean.TRUE.equals(rc.getAlwaysOn());
            sheetAlwaysOn.merge(rc.getSheetName(), ao, (a, b) -> a && b);
        }

        List<RuleGroup> built = new ArrayList<>();
        rulesBySheet.forEach((sheet, rules) ->
                built.add(new RuleGroup(sheet, sheetAlwaysOn.getOrDefault(sheet, false), rules)));

        this.groups = built;
        log.debug("[RuleCatalog] refreshed — {} rules ({} enabled), {} sheet groups",
                all.size(), groups.stream().mapToInt(g -> g.getRules().size()).sum(), built.size());
    }

    // ── UI accessors (unchanged signatures) ──────────────────────────────────

    public List<RuleGroup> getGroups() {
        if (groups.isEmpty() && repo.count() > 0) refresh(); // lazy safety net
        return groups;
    }

    public List<String> getToggleableRuleIds() {
        List<String> ids = new ArrayList<>();
        getGroups().stream().filter(g -> !g.isAlwaysOn())
                .forEach(g -> g.getRules().forEach(r -> ids.add(r.getId())));
        return ids;
    }

    public int getToggleableRuleCount() {
        return getToggleableRuleIds().size();
    }

    // ── Engine gating + admin ─────────────────────────────────────────────────

    /**
     * True if the rule is globally enabled. Rules unknown to the catalog return
     * true so the engine never silently drops rules it doesn't manage.
     */
    public boolean isGloballyEnabled(String ruleId) {
        return enabledById.getOrDefault(ruleId, Boolean.TRUE);
    }

    /** Configured message template for a rule, or null if it uses the default. */
    public String getMessageTemplate(String ruleId) {
        return messageById.get(ruleId);
    }

    /** All rules (enabled and disabled) for the admin management screen. */
    public List<RuleConfig> getAllRules() {
        return repo.findAllByOrderBySortOrderAscIdAsc();
    }

    /** All rules grouped by sheet (insertion-ordered) for the tabbed admin UI. */
    public java.util.Map<String, List<RuleConfig>> getAllRulesBySheet() {
        java.util.Map<String, List<RuleConfig>> bySheet = new java.util.LinkedHashMap<>();
        for (RuleConfig rc : repo.findAllByOrderBySortOrderAscIdAsc()) {
            String sheet = rc.getSheetName() == null ? "Other" : rc.getSheetName();
            bySheet.computeIfAbsent(sheet, k -> new ArrayList<>()).add(rc);
        }
        return bySheet;
    }

    /** Enable/disable a rule in the DB and refresh the snapshot immediately. */
    public void setEnabled(String ruleId, boolean enabled) {
        repo.findByRuleId(ruleId).ifPresent(rc -> {
            rc.setEnabled(enabled);
            repo.save(rc);
            log.info("[RuleCatalog] rule {} set enabled={}", ruleId, enabled);
        });
        refresh();
    }

    // ── CRUD (admin add / edit / delete) ──────────────────────────────────────

    public java.util.Optional<RuleConfig> getRule(Long id) {
        return repo.findById(id);
    }

    public boolean ruleIdExists(String ruleId) {
        return ruleId != null && repo.existsByRuleId(ruleId.trim());
    }

    /** Create or update a rule, then refresh the snapshot. */
    public void saveRule(RuleConfig rc) {
        repo.save(rc);
        refresh();
        log.info("[RuleCatalog] saved rule {}", rc.getRuleId());
    }

    public void deleteRule(Long id) {
        repo.findById(id).ifPresent(rc -> {
            repo.delete(rc);
            log.info("[RuleCatalog] deleted rule {}", rc.getRuleId());
        });
        refresh();
    }
}
