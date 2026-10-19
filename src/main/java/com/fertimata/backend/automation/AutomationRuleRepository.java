package com.fertimata.backend.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByEnabledTrueOrderByPriorityAsc();
}
