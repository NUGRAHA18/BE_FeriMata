package com.fertimata.backend.automation;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A stored automation rule.
 *
 * <p>The table exists so that phase 2 does not start with a migration, but <b>no rule is evaluated
 * in this phase</b> - see {@link AutomationEngine}. Condition and action are opaque JSON documents
 * because their grammar depends on decisions nobody has made yet: which sensors exist, which
 * actuators exist, what the safe thresholds are, and what interlocks must hold before a pump may
 * run unattended.
 *
 * <p>TODO(team): agree on the condition/action grammar before writing the evaluator. Guessing it
 * here would produce rules that cannot express the safety constraints the agronomy team will ask for.
 */
@Entity
@Table(name = "automation_rule")
@Getter
@Setter
@NoArgsConstructor
public class AutomationRule extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_expression", columnDefinition = "jsonb")
    private String conditionExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_expression", columnDefinition = "jsonb")
    private String actionExpression;

    /** Lower numbers evaluate first once an evaluator exists. */
    @Column(nullable = false)
    private int priority = 100;
}
