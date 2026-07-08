package com.timesheet.validator.config;

import com.timesheet.validator.domain.RuleConfig;
import com.timesheet.validator.repository.RuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the {@code RULE_CONFIG} table from {@code app.rules.definitions} (YAML).
 *
 * <p>Upsert semantics: a rule is inserted from YAML only if it does not already
 * exist. Existing rows are left untouched, so admin add/edit/enable changes are
 * authoritative and survive restarts. Set {@code app.rules.reseed-on-start=true}
 * to force YAML to overwrite existing rows.</p>
 *
 * <p>Runs as an {@link ApplicationRunner} (before {@code ApplicationReadyEvent}),
 * so the catalog's ready-time refresh sees the seeded rows.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class RuleConfigLoader implements ApplicationRunner {

    private final RuleProperties        props;
    private final RuleConfigRepository  repo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.getDefinitions() == null || props.getDefinitions().isEmpty()) {
            log.warn("[RuleConfigLoader] No rules defined under app.rules.definitions");
            return;
        }

        int inserted = 0, reseeded = 0;
        for (RuleProperties.RuleDefProps d : props.getDefinitions()) {
            if (d.getRuleId() == null || d.getRuleId().isBlank()) continue;

            RuleConfig existing = repo.findByRuleId(d.getRuleId().trim()).orElse(null);

            if (existing == null) {
                // New rule from YAML — insert with all YAML values.
                repo.save(RuleConfig.builder()
                        .ruleId(d.getRuleId().trim())
                        .sheetName(d.getSheet())
                        .description(d.getDescription())
                        .severity(d.getSeverity())
                        .alwaysOn(d.isAlwaysOn())
                        .enabled(d.isEnabled())
                        .sortOrder(d.getSortOrder())
                        .messageTemplate(d.getMessageTemplate())
                        .build());
                inserted++;
            } else if (props.isReseedOnStart()) {
                // Explicit opt-in: YAML overwrites the existing row entirely.
                existing.setSheetName(d.getSheet());
                existing.setDescription(d.getDescription());
                existing.setSeverity(d.getSeverity());
                existing.setAlwaysOn(d.isAlwaysOn());
                existing.setEnabled(d.isEnabled());
                existing.setSortOrder(d.getSortOrder());
                existing.setMessageTemplate(d.getMessageTemplate());
                repo.save(existing);
                reseeded++;
            }
            // Otherwise leave the existing row untouched — the DB (and any admin
            // add/edit/enable changes) is authoritative once seeded.
        }
        log.info("[RuleConfigLoader] Rule config seeded — inserted={} reseeded={} total={}",
                inserted, reseeded, repo.count());
    }
}
