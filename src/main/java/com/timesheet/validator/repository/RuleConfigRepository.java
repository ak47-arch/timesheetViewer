package com.timesheet.validator.repository;

import com.timesheet.validator.domain.RuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleConfigRepository extends JpaRepository<RuleConfig, Long> {

    List<RuleConfig> findAllByOrderBySortOrderAscIdAsc();

    List<RuleConfig> findByEnabledTrueOrderBySortOrderAscIdAsc();

    Optional<RuleConfig> findByRuleId(String ruleId);

    boolean existsByRuleId(String ruleId);
}
